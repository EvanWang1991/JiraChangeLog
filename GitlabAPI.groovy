import groovy.json.*
import groovy.json.internal.LazyMap

//GitLabAPI class that is responsible for connection to GitLab and retrieving the integrationFlow file from the SuiteIntegration Project.
public class GitlabAPI{

	//System Variables
	def team_name = System.getenv("team_name")
	def team_name_dash = System.getenv("team_name_dash")
	def team_repo = System.getenv("team_repo")
	def team = System.getenv("team")

	// GitLab Variables
	def gitUrl = 'http://gitlab.openjawtech.com/api/v4/'

	//Define the setters
	def privateToken = 'ebz1ExFitmQASbHKJQkP'

	//Retrieve the ProjectID (Used for the API) when searching for the Project by name.
	def getProjectId(def projectName, def teamRepo){
		def projectID
		try{
			def projects = getRequestParsedToJson("${gitUrl}groups/"+ teamRepo +"/projects?search="+ projectName +"&private_token="+ privateToken +"&simple=true")
			projects.each {
				//Need to check if the name matches exactly, since certain projects have similar names xPromotion/xPromotionSrc
				if(it.path_with_namespace.equalsIgnoreCase(teamRepo + "/" + projectName)){
					projectID = it.id
					return projectID
				}
			}
		} catch(Exception e){
			throw new RuntimeException("Project:" + projectName + " could not be found, Please ensure your project exists within the repo.")
		}
		return projectID
	}
	
	//Retrieve the IntegrationFlow file from the Teams Suiteintegration project
	def getIntegrationFlow(){
		def projectID = getProjectId('SuiteIntegration', team_repo)
		def endpoint = "${gitUrl}projects/"+ projectID +"/repository/files/integrationFlow%2Ejson/raw?ref="+ team_name +"&private_token="+ privateToken
		def jsonIntegrationFlow = getRequestParsedToJson(endpoint)
		return jsonIntegrationFlow
	}

	//Retrieve the Jenkins Properties file from the Teams Suiteintegration project
	def getJenkinsProperties(){
		def projectID = getProjectId('SuiteIntegration', team_repo)
		def endpoint = "${gitUrl}projects/"+ projectID +"/repository/files/jenkinsProperties%2Ejson/raw?ref="+ team_name +"&private_token="+ privateToken
		def jsonJenkinsProperties = getRequestParsedToJson(endpoint)
		return jsonJenkinsProperties
	}

	//Return the project object from the Jenkins Integration Flow.
	def getProjectFromIntegrationFlow(def projectName, def team_name, def team_repo){
		LazyMap project = new LazyMap()
		def integrationFlow = getIntegrationFlow()
		//Iterate over the integrationFlow
		integrationFlow.each {stage ->
			def projectArray = stage.value
			projectArray.each {
				if(it.name.toLowerCase().equals(projectName.toLowerCase())) {
					project = it
					return
				}
			}
		}
		return project
	}
	
	//Convert Response to JSON
	def getRequestParsedToJson(def requestUrl){
		def url = new URL(requestUrl)
		def response = url.newReader().getText()
		return new JsonSlurper().parseText(response)
	}
	
	//Retrieve the latest tag from a project in the OpenJaw Namespace
	def getTagName(def ProjectName) {
		def projectID = getProjectId(ProjectName, 'OpenJaw')
		def endpoint = "${gitUrl}projects/"+ projectID +"/repository/tags?private_token="+ privateToken
		def jsonTags = getRequestParsedToJson(endpoint)
		//Return the name of the first tag return (by default the last tag to be made.)
		return jsonTags.first().name
	}
	
	//Determine if a project is in the OpenJaw or OpenJawConnectors NameSpace.
	boolean inOJGroup (def ProjectName) {
		def returnValue = false
		def OpenJawProjectID = getProjectId(ProjectName, 'OpenJaw')
		def OpenJawConnectorsProjectID = getProjectId(ProjectName, 'OpenJaw-Connectors')
		
		if (OpenJawProjectID || OpenJawConnectorsProjectID) {
			returnValue = true
		}
		return returnValue
	}
	
	//Determine if a project is a connector.
	boolean isOJConnector (def ProjectName) {
		def returnValue = false
		def OpenJawProjectID = getProjectId(ProjectName, 'OpenJaw')
		def OpenJawConnectorsProjectID = getProjectId(ProjectName, 'OpenJaw-Connectors')
		
		if (OpenJawConnectorsProjectID) {
			returnValue = true
		}
		return returnValue
	}
	
	//Retrieve a file located in Git
	def retrieveFile (def projectName, def team_name, def team_repo, def file) {
		def response
		try {
			def projectID = getProjectId(projectName, team_repo)
			def endpoint = "${gitUrl}projects/"+ projectID +"/repository/files/"+ file +"/raw?ref="+ team_name +"&private_token="+ privateToken
			def url = new URL(endpoint)
			response = url.newReader().getText()
		} catch ( FileNotFoundException e) {
			println file + " was not found, Please check variables and try again!"
			println "Project Name:" + projectName
			println "Team Name:" + team_name
			println "Git Repo:" + team_repo
			return ""
		}
		return response
	}
	
}
