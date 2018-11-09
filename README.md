# HealthCheckServicesMediator
This is a mediator that I created to pull the service status (Active/Inactive) in ESB 4.8.1. But it should work/be adaptable for newer versions as well.

This version happens to return an Atom document type since I needed that for Software AG MashZone. The output docuemnt could easily be replaced with a custom XML payload if desired.


## Sample API module to call mediator
<?xml version="1.0" encoding="UTF-8"?>
<api context="/healthCheck/1" name="HealthCheck_v1" xmlns="http://ws.apache.org/ns/synapse">
   	
  	<resource 
  	methods="GET" 
  	protocol="http" 
  	url-mapping="/health/services"
  	inSequence="HealthCheck_ServicesSequence"
  	faultSequence="HealthCheck_FaultSequence"
  	/>
</api>

<sequence xmlns="http://ws.apache.org/ns/synapse" name="HealthCheck_ServicesSequence" onError="HealthCheck_FaultSequence" trace="disable">
   <class name="tsc.ssc.ai.esb.mediators.HealthCheckServicesMediator"/>
   <property name="Access-Control-Allow-Origin" value="*" scope="transport" type="STRING"/>
   <property name="Access-Control-Allow-Credential" value="true" scope="transport" type="STRING"/> 
   <respond/>
</sequence>

## Deployment
Place these jars in $PATH_TO_ESB/wso2esb/repository/components/lib
  slf4j-api-1.7.16.jar
  jdom2-2.0.6.jar
  rome-1.11.0.jar
  rome-utils-1.11.0.jar
  HealthCheckMediators-3.1.1.1.jar
 
