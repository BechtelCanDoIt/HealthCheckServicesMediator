package tsc.ssc.ai.esb.HealthCheckMediators;

import java.util.Properties;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.synapse.Mediator;
import org.apache.synapse.MessageContext;
import org.apache.synapse.TestMessageContext;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.xml.MediatorFactoryFinder;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.Axis2SynapseEnvironment;
import org.apache.synapse.mediators.AbstractMediatorTestCase;
import org.apache.synapse.mediators.base.SequenceMediator;

import tsc.ssc.ai.esb.mediators.HealthCheckServicesMediator;

/**
 * Unit test for simple App.
 * @see https://github.com/wso2/wso2-synapse/blob/master/modules/core/src/test/java/org/apache/synapse/mediators/eip/AbstractSplitMediatorTestCase.java
 */
public class HealthCheckServicesMediatorTest extends AbstractMediatorTestCase {
	Axis2MessageContext testCtx;
	//MediatorFactory fac;
	
	public void setUp() throws Exception {
		super.setUp();
		SynapseConfiguration synCfg = new SynapseConfiguration();
		AxisConfiguration config = new AxisConfiguration();
		testCtx = new Axis2MessageContext(new org.apache.axis2.context.MessageContext(), synCfg,
				new Axis2SynapseEnvironment(new ConfigurationContext(config), synCfg));
		((Axis2MessageContext) testCtx).getAxis2MessageContext()
				.setConfigurationContext(new ConfigurationContext(config));
		SOAPEnvelope envelope = OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope();
		envelope.getBody().addChild(createOMElement("<original>test-split-context</original>"));
		testCtx.setEnvelope(envelope);
		testCtx.setSoapAction("urn:test");
		SequenceMediator seqMed = new SequenceMediator();		
		testCtx.getConfiguration().addSequence("seqRef", seqMed);
		testCtx.getConfiguration().addSequence("main", new SequenceMediator());
		testCtx.getConfiguration().addSequence("fault", new SequenceMediator());
		testCtx.setEnvironment(new Axis2SynapseEnvironment(new SynapseConfiguration()));
	}

	protected void tearDown() throws Exception {
        super.tearDown();
        testCtx = null;
    }

	
	private static OMFactory fac = OMAbstractFactory.getOMFactory();

	public void testClassLoadMediatorType() throws Exception {
		Mediator cm = MediatorFactoryFinder.getInstance()
				.getMediator(createOMElement("<class name='tsc.ssc.ai.esb.mediators.HealthCheckServicesMediator' "
						+ "xmlns='http://ws.apache.org/ns/synapse'/>"), new Properties());
		assertTrue(cm.getType().equals("ClassMediator"));
	}
	
	public void testClassLoadMediation() throws Exception {
		Mediator cm = MediatorFactoryFinder.getInstance()
				.getMediator(createOMElement("<class name='tsc.ssc.ai.esb.mediators.HealthCheckServicesMediator' "
						+ "xmlns='http://ws.apache.org/ns/synapse'/>"), new Properties());
		assertTrue(cm.mediate(testCtx));
	}

	public void testHCSMMediation() throws Exception {
		HealthCheckServicesMediator m = new HealthCheckServicesMediator();
		assertTrue(m.mediate(testCtx));
	}
}
