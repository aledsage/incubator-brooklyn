package com.cloudsoftcorp.monterey.brooklyn.entity

import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Collection
import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.BrooklynSystemProperties
import brooklyn.util.internal.EntityStartUtils

import com.cloudsoftcorp.monterey.clouds.NetworkId
import com.cloudsoftcorp.monterey.clouds.basic.DeploymentUtils
import com.cloudsoftcorp.monterey.clouds.dto.CloudEnvironmentDto
import com.cloudsoftcorp.monterey.control.api.SegmentSummary
import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NetworkInfo
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.DeploymentWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.Dmn1NetworkInfoWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.PingWebProxy
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.deployment.MontereyDeploymentDescriptor
import com.cloudsoftcorp.monterey.network.m.MediationWorkrateItem.MediationWorkrateItemNames
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.TimeUtils
import com.cloudsoftcorp.util.exception.ExceptionUtils
import com.cloudsoftcorp.util.exception.RuntimeWrappedException
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.osgi.BundleSet
import com.cloudsoftcorp.util.proc.ProcessExecutionFailureException
import com.cloudsoftcorp.util.web.client.CredentialsConfig
import com.cloudsoftcorp.util.web.server.WebConfig
import com.cloudsoftcorp.util.web.server.WebServer
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.google.gson.Gson


/**
 * Represents a Monterey network.
 * 
 * @author aled
 */
public class MontereyNetwork extends AbstractEntity implements Startable { // FIXME , AbstractGroup

    private final Logger LOG = Loggers.getLogger(MontereyNetwork.class);

    private static final Logger logger = Loggers.getLogger(MontereyNetwork.class);

    public static final BasicAttributeSensor<Integer> MANAGEMENT_URL = [ URL.class, "monterey.management-url", "Management URL" ]
    public static final BasicAttributeSensor<String> NETWORK_ID = [ String.class, "monterey.network-id", "Network id" ]
    public static final BasicAttributeSensor<String> APPLICTION_NAME = [ String.class, "monterey.application-name", "Application name" ]

    /** up, down, etc? */
    public static final BasicAttributeSensor<String> STATUS = [ String, "monterey.status", "Status" ]

    private static final int POLL_PERIOD = 1000;
    
    private final Gson gson;

    private String installDir;
    private MontereyNetworkConfig config;
    private Collection<UserCredentialsConfig> webUsersCredentials;
    private CredentialsConfig webAdminCredential;
    private NetworkId networkId = NetworkId.Factory.newId();

    private SshMachineLocation host;
    private URL managementUrl;
    private MontereyNetworkConnectionDetails connectionDetails;
    private String applicationName;

    private final LocationRegistry locationRegistry = new LocationRegistry();
    private final Map<NodeId,MontereyContainerNode> nodes = new ConcurrentHashMap<NodeId,MontereyContainerNode>();
    private final Map<String,Segment> segments = new ConcurrentHashMap<String,Segment>();
    private final Map<Location,MediatorGroup> mediatorsByLocation = new ConcurrentHashMap<Location,MediatorGroup>();
    private ScheduledFuture<?> monitoringTask;
    
    public MontereyNetwork() {
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }

    public void setInstallDir(String val) {
        this.installDir = val;
    }

    public void setConfig(MontereyNetworkConfig val) {
        this.config = val;
    }

    public void setWebUsersCredentials(Collection<UserCredentialsConfig> val) {
        this.webUsersCredentials = val;
        this.webAdminCredential = DeploymentUtils.findWebApiAdminCredential(webUsersCredentials);
    }

    public void setWebAdminCredential(CredentialsConfig val) {
        this.webAdminCredential = val;
    }

    public void setNetworkId(NetworkId val) {
        networkId = val;
    }

    // FIXME Use attributes instead of getters
    public String getManagementUrl() {
        return managementUrl;
    }

    public Map<NodeId,MontereyContainerNode> getContainerNodes() {
        return ImmutableMap.copyOf(nodes);
    }

    public Map<NodeId,AbstractMontereyNode> getMontereyNodes() {
        Map<NodeId,AbstractMontereyNode> result = [:]
        nodes.values().each {
            result.put(it.getNodeId(), it.getContainedMontereyNode());
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<Location, MediatorGroup> getMediatorGroups() {
        mediatorsByLocation.asImmutable();
    }
    
    public Map<String,Segment> getSegments() {
        return ImmutableMap.copyOf(this.@segments);
    }

    @VisibleForTesting    
    LocationRegistry getLocationRegistry() {
        return locationRegistry;
    }

    public void start(Collection<? extends Location> locs) {
        // FIXME Work in progress...
        EntityStartUtils.startEntity this, locs
        LOG.debug "Monterey network started... management-url is {}", this.properties['ManagementUrl']
    }
    
    public void dispose() {
        if (monitoringTask != null) monitoringTask.cancel(true);
    }

    public void startOnHost(SshMachineLocation host) {
        /*
         * TODO: Assumes the following are already set on SshMachine:
         * sshAddress
         * sshPort
         * sshUsername
         * sshKey/sshKeyFile
         * HostKeyChecking hostKeyChecking = HostKeyChecking.NO;
         */

        LOG.info("Creating new monterey network "+networkId+" on "+host);

        File webUsersConfFile = DeploymentUtils.toEncryptedWebUsersConfFile(webUsersCredentials);
        String username = System.getenv("USER");

        WebConfig web = new WebConfig(true, config.getMontereyWebApiPort(), config.getMontereyWebApiProtocol(), null);
        web.setSslKeystore(installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH);
        web.setSslKeystorePassword(config.getMontereyWebApiSslKeystorePassword());
        web.setSslKeyPassword(config.getMontereyWebApiSslKeyPassword());
        File webConf = DeploymentUtils.toWebConfFile(web);

        try {
            host.copyTo(webUsersConfFile, installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEBUSERS_FILE_RELATIVE_PATH);

            if (config.getLoggingFileOverride() != null) {
                host.copyTo(config.getLoggingFileOverride(), installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_LOGGING_FILE_OVERRIDE_RELATIVE_PATH);
                host.copyTo(config.getLoggingFileOverride(), installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_LOGGING_FILE_RELATIVE_PATH);
            }

            host.copyTo(webConf, installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH);
            if (config.getMontereyWebApiProtocol().equals(WebServer.HTTPS)) {
                host.copyTo(config.getMontereyWebApiSslKeystore(), installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH);
            }

            this.managementUrl = new URL(config.getMontereyWebApiProtocol()+"://"+host.getAddress().getHostName()+":"+config.getMontereyWebApiPort());
            this.connectionDetails = new MontereyNetworkConnectionDetails(networkId, managementUrl, webAdminCredential);
            this.host = host;

            // Convenient for testing: create the management-node directly in-memory, rather than starting it in a separate process
            // Please leave this commented out code here, to make subsequent debugging easier!
            // Or you could refactor to have a private static final constant that switches the behaviour?
            //            MainArguments mainArgs = new MainArguments(new File(installDir), null, null, null, null, null, networkId.getId());
            //            new ManagementNodeStarter(mainArgs).start();

            host.run(out: System.out,
                    installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_START_SCRIPT_RELATIVE_PATH+
                    " -address "+host.getAddress().getHostName()+
                    " -port "+Integer.toString(config.getMontereyNodePort())+
                    " -networkId "+networkId.getId()+
                    " -key "+networkId.getId()+
                    " -webConfig "+installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH+";"+
                    "exit");

            PingWebProxy pingWebProxy = new PingWebProxy(managementUrl.toString(), webAdminCredential,
                    (config.getMontereyWebApiSslKeystore() != null ? config.getMontereyWebApiSslKeystore().getPath() : null),
                    config.getMontereyWebApiSslKeystorePassword());
            boolean reachable = pingWebProxy.waitForReachable(MontereyNetworkConfig.TIMEOUT_FOR_NEW_NETWORK_ON_HOST);
            if (!reachable) {
                throw new IllegalStateException("Management plane not reachable via web-api within "+TimeUtils.makeTimeString(MontereyNetworkConfig.TIMEOUT_FOR_NEW_NETWORK_ON_HOST)+": url="+managementUrl);
            }

            setAttribute MANAGEMENT_URL, managementUrl
            setAttribute NETWORK_ID, networkId.getId()

            monitoringTask = Executors.newScheduledThreadPool(1).scheduleWithFixedDelay({ updateAll() }, POLL_PERIOD, POLL_PERIOD, TimeUnit.MILLISECONDS)

            LOG.info("Created new monterey network: "+connectionDetails);

        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error creating monterey network", e);

            if (BrooklynSystemProperties.DEBUG.isEnabled()) {
                // Not releasing failed instance, because that would make debugging hard!
                LOG.log(Level.WARNING, "Error creating monterey network; leaving failed instance "+host, e);
            } else {
                LOG.log(Level.WARNING, "Error creating monterey network; terminating failed instance "+host, e);
                try {
                    shutdownManagementNodeProcess(config, host, networkId);
                } catch (ProcessExecutionFailureException e2) {
                    LOG.log(Level.WARNING, "Error cleaning up monterey network after failure to start: machine="+host, e2);
                }
            }

            throw new RuntimeWrappedException("Error creating monterey network on "+host, e);
        }
    }

    public void deployCloudEnvironment(CloudEnvironmentDto cloudEnvironmentDto) {
        int DEPLOY_TIMEOUT = 5*60*1000;
        DeploymentWebProxy deployer = new DeploymentWebProxy(managementUrl, gson, webAdminCredential, DEPLOY_TIMEOUT);
        deployer.deployCloudEnvironment(cloudEnvironmentDto);
    }

    public void deployApplication(MontereyDeploymentDescriptor descriptor, BundleSet bundles) {
        int DEPLOY_TIMEOUT = 5*60*1000;
        DeploymentWebProxy deployer = new DeploymentWebProxy(managementUrl, gson, webAdminCredential, DEPLOY_TIMEOUT);
        boolean result = deployer.deployApplication(descriptor, bundles);
    }

    public void stop() {
        // TODO Guard so can only shutdown if network nodes are not running?
        if (host == null) {
            throw new IllegalStateException("Monterey network is not running; cannot stop");
        }
        shutdownManagementNodeProcess(this.config, host, networkId)
        
        // TODO Race: monitoringTask could still be executing, and could get NPE when it tries to get connectionDetails
        if (monitoringTask != null) monitoringTask.cancel(true);
        
        host = null;
        managementUrl = null;
        connectionDetails = null;
        applicationName = null;
    }

    private void shutdownManagementNodeProcess(MontereyNetworkConfig config, SshMachineLocation host, NetworkId networkId) {
        String killScript = installDir+"/"+MontereyNetworkConfig.MANAGER_SIDE_KILL_SCRIPT_RELATIVE_PATH;
        try {
            LOG.info("Releasing management node on "+toString());
            host.run(out: System.out,
                    killScript+" -key "+networkId.getId()+";"+
                    "exit");

        } catch (IllegalStateException e) {
            if (e.toString().contains("No such process")) {
                // the process hadn't started or was killed externally? Our work is done.
                LOG.info("Management node process not running; termination is a no-op: networkId="+networkId+"; machine="+host);
            } else {
                LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+host, e);
            }
        } catch (ProcessExecutionFailureException e) {
            LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+host, e);

        } catch (IOException e) {
            LOG.log(Level.WARNING, "Error termining monterey management node process: networkId="+networkId+"; machine="+host, e);
        }
    }

    private void updateAll() {
        try {
            boolean isup = updateStatus();
            if (isup) {
                updateAppName();
                updateTopology();
                updateWorkrates();
            }
        } catch (Throwable t) {
            LOG.log Level.WARNING, "Error updating brooklyn entities of Monterey Network "+managementUrl, t
            ExceptionUtils.throwRuntime t
        }
    }

    private boolean updateStatus() {
        PingWebProxy pinger = new PingWebProxy(connectionDetails.getManagementUrl(), connectionDetails.getWebApiAdminCredential());
        boolean isup = pinger.ping();
        String status = (isup) ? "UP" : "DOWN";
        setAttribute(STATUS, status);
        return isup;
    }
    
    private void updateAppName() {
        DeploymentWebProxy deployer = new DeploymentWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        MontereyDeploymentDescriptor currentApp = deployer.getApplicationDeploymentDescriptor();
        String currentAppName = currentApp?.getName();
        if (!(applicationName != null ? applicationName.equals(currentAppName) : currentAppName == null)) {
            applicationName = currentAppName;
            setAttribute(APPLICTION_NAME, applicationName);
        }
    }
    
    private void updateTopology() {
        Dmn1NetworkInfo networkInfo = new Dmn1NetworkInfoWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        Map<NodeId, NodeSummary> nodeSummaries = networkInfo.getNodeSummaries();
        Map<String, SegmentSummary> segmentSummaries = networkInfo.getSegmentSummaries();
        Map<String, NodeId> segmentAllocations = networkInfo.getSegmentAllocations();
        Map<NodeId,Collection<NodeId>> downstreamNodes = networkInfo.getTopology().getAllTargets();
        Collection<MontereyActiveLocation> montereyLocations = networkInfo.getActiveLocations();
        Collection<Location> locations = montereyLocations.collect { locationRegistry.getConvertedLocation(it) }
        
        // Create/destroy mediator groups
        Collection<Location> newLocations = []
        Collection<Location> removedLocations = []
        newLocations.addAll(locations); newLocations.removeAll(mediatorsByLocation.keySet());
        removedLocations.addAll(mediatorsByLocation.keySet()); removedLocations.removeAll(locations);

        newLocations.each {
            MediatorGroup mediatorGroup = new MediatorGroup(connectionDetails, it);
            addOwnedChild(mediatorGroup);
            mediatorsByLocation.put(it, mediatorGroup);
        }

        removedLocations.each {
            MediatorGroup mediatorGroup = mediatorsByLocation.get(it);
            if (mediatorGroup != null) {
                mediatorGroup.dispose();
                removeOwnedChild(mediatorGroup);
            }
        }

        
        // Create/destroy nodes that have been added/removed
        Collection<NodeId> newNodes = []
        Collection<NodeId> removedNodes = []
        newNodes.addAll(nodeSummaries.keySet()); newNodes.removeAll(nodes.keySet());
        removedNodes.addAll(nodes.keySet()); removedNodes.removeAll(nodeSummaries.keySet());

        newNodes.each {
            MontereyActiveLocation montereyLocation = nodeSummaries.get(it).getMontereyActiveLocation();
            Location location = locationRegistry.getConvertedLocation(montereyLocation);
            MontereyContainerNode containerNode = new MontereyContainerNode(connectionDetails, it, location);
            addOwnedChild(containerNode);
            nodes.put(it, containerNode);
        }

        removedNodes.each {
            MontereyContainerNode node = nodes.get(it);
            if (node != null) {
                node.dispose();
                removeOwnedChild(node);
            }
        }


        // Create/destroy segments
        Collection<NodeId> newSegments = []
        Collection<NodeId> removedSegments = []
        newSegments.addAll(segmentSummaries.keySet()); newSegments.removeAll(segments.keySet());
        removedSegments.addAll(segments.keySet()); removedSegments.removeAll(segmentSummaries.keySet());

        newSegments.each {
            Segment segment = new Segment(connectionDetails, it);
            addOwnedChild(segment);
            this.@segments.put(it, segment);
        }

        removedSegments.each {
            Segment segment = this.@segments.remove(it);
            if (segment != null) {
                segment.dispose();
                removeOwnedChild(segment);
            }
        }

        // Notify segments of their mediator
        segments.values().each {
            String segmentId = it.segmentId()
            SegmentSummary summary = segmentSummaries.get(segmentId);
            NodeId mediator = segmentAllocations.get(segmentId);
            it.updateTopology(summary, mediator);  
        }
        
        // Notify "container nodes" (i.e. BasicNode in monterey classes jargon) of what node-types are running there
        nodeSummaries.values().each {
            nodes.get(it.getNodeId())?.updateContents(it, downstreamNodes.get(it.getNodeId()));
        }
    }

    private void updateWorkrates() {
        Dmn1NetworkInfo networkInfo = new Dmn1NetworkInfoWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        Map<NodeId, WorkrateReport> workrates = networkInfo.getActivityModel().getAllWorkrateReports();

        workrates.entrySet().each {
            WorkrateReport report = it.getValue();

            // Update this node's workrate
            nodes.get(it.getKey())?.updateWorkrate(report);

            // Update each segment's workrate (if a mediator's segment-workrate item is contained here)
            report.getWorkrateItems().each {
                String itemName = it.getName()
                if (MediationWorkrateItemNames.isNameForSegment(itemName)) {
                    String segmentId = MediationWorkrateItemNames.segmentFromName(itemName);
                    segments.get(segmentId)?.updateWorkrate(report);
                }
            }
        }
    }
}
