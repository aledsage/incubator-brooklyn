package brooklyn.entity.messaging.activemq

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Preconditions

/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBroker extends JavaApp {
    private static final Logger log = LoggerFactory.getLogger(ActiveMQBroker.class)

    public static final ConfiguredAttributeSensor<Integer> OPEN_WIRE_PORT = [ Integer, "openwire.port", "OpenWire port", 61616 ]

    String virtualHost
    Collection<String> queueNames = []
    Map<String, ActiveMQQueue> queues = [:]
    Collection<String> topicNames = []
    Map<String, ActiveMQTopic> topics = [:]

    public ActiveMQBroker(Map properties=[:]) {
        super(properties)

        setConfigIfValNonNull(OPEN_WIRE_PORT.configKey, properties.openWirePort)

        setAttribute(Attributes.JMX_USER, properties.user ?: "admin")
        setAttribute(Attributes.JMX_PASSWORD, properties.password ?: "admin")

        if (properties.queue) queueNames.add properties.queue
        if (properties.queues) queueNames.addAll properties.queues

        if (properties.topic) topicNames.add properties.topic
        if (properties.topics) topicNames.addAll properties.topics
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return ActiveMQSetup.newInstance(this, machine)
    }

    @Override
    public void addJmxSensors() {
        attributePoller.addSensor(JavaApp.SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }

    public void postStart() {
        queueNames.each { String name -> createQueue(name) }
        topicNames.each { String name -> createTopic(name) }
    }

    public void preStop() {
        queues.each { String name, ActiveMQQueue queue -> queue.destroy() }
        topics.each { String name, ActiveMQTopic topic -> topic.destroy() }
    }

    public void createQueue(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        queues.put name, new ActiveMQQueue(properties)
    }

    public void createTopic(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        topics.put name, new ActiveMQTopic(properties)
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['openWirePort']
    }

    protected boolean computeNodeUp() {
        ValueProvider<String> provider = jmxAdapter.newAttributeProvider("org.apache.activemq:type=ServerInformation,name=ServerInformation", "ProductVersion")
        try {
            String productVersion = provider.compute()
	        return (productVersion == getAttribute(Attributes.VERSION))
        } catch (InstanceNotFoundException infe) {
            return false
        }
    }
}

public abstract class ActiveMQBinding extends AbstractEntity {
    String virtualHost

    protected ObjectName virtualHostManager
    protected ObjectName exchange

    transient JmxSensorAdapter jmxAdapter
    transient AttributePoller attributePoller

    public ActiveMQBinding(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkNotNull name, "Name must be specified"

        init()

        jmxAdapter = ((ActiveMQBroker) getOwner()).jmxAdapter
        attributePoller = new AttributePoller(this)

        create()
    }

    public abstract void init()

    public abstract void addJmxSensors()

    public abstract void removeJmxSensors()

    public void create() {
        jmxAdapter.operation(virtualHostManager, "createNewQueue", name, getOwner().getAttribute(Attributes.JMX_USER), true)
        jmxAdapter.operation(exchange, "createNewBinding", name, name)
        addJmxSensors()
    }

    public void delete() {
        jmxAdapter.operation(exchange, "removeBinding", name, name)
        jmxAdapter.operation(virtualHostManager, "deleteQueue", name)
        removeJmxSensors()
    }

    /**
     * Return the ActiveMQ BURL name for the queue.
     */
    public String getBindingUrl() { return String.format("BURL:%s", name) }

    @Override
    public void destroy() {
		attributePoller.close()
        super.destroy()
	}

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}

public class ActiveMQQueue extends ActiveMQBinding implements Queue {
    public ActiveMQQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        setAttribute QUEUE_NAME, name
        exchange = new ObjectName("org.apache.activemq:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.direct\",ExchangeType=direct")
    }

    public void addJmxSensors() {
        String queue = "org.apache.activemq:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.addSensor(QUEUE_DEPTH, jmxAdapter.newAttributeProvider(queue, "QueueDepth"))
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(queue, "MessageCount"))
    }

    public void removeJmxSensors() {
        String queue = "org.apache.activemq:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.removeSensor(QUEUE_DEPTH)
        attributePoller.removeSensor(MESSAGE_COUNT)
    }
}

public class ActiveMQTopic extends ActiveMQBinding implements Topic {
    public ActiveMQTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        setAttribute TOPIC_NAME, name
        exchange = new ObjectName("org.apache.activemq:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.topic\",ExchangeType=topic")
    }

    public void addJmxSensors() {
        String topic = "org.apache.activemq:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.addSensor(QUEUE_DEPTH, jmxAdapter.newAttributeProvider(topic, "QueueDepth"))
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(topic, "MessageCount"))
    }

    public void removeJmxSensors() {
        String topic = "org.apache.activemq:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.removeSensor(QUEUE_DEPTH)
        attributePoller.removeSensor(MESSAGE_COUNT)
    }
}
