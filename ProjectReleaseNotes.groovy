import groovy.transform.Field
import groovy.xml.MarkupBuilder
import hudson.model.*
import GitLog
import Jira
import GitlabAPI

import java.util.regex.Matcher
import java.util.regex.Pattern

@Field final filters = ["Merge branch.*\\b","\\b.*Merge remote-tracking branch.*\\b","\\b.*PLATDEV-0.*\\b"]
@Field final gitUrl = "http://git@gitlab.openjawtech.com/"
@Field final team_repo = "Orchid"
@Field final workspace = System.getenv()['WORKSPACE']


def project = getProjectName()

final String INTEGRATION_BRANCH = args[0]
final String MASTER_BRANCH = args[1]
final String PatchRelease = args[2]


def commandOutPut = executeCommand("git branch -a")

//Define the supported JIRA Names that are supported.
SupportedJIRAList = ['PLATDEV', 'OSS', 'TRASKY', 'PL', 'TNP', 'SNP', 'PROXTM', 'AI', 'ALL', 'DX']

//Ensure the command is returned.
int count = 0;  
while(!commandOutPut && count<10) {
	sleep 500
	println "Git Branch command returned nothing! retrying ...: " + count
	count++
	commandOutPut = executeCommand("git branch -a")
	if(count.equals(9) && !command) throw new Exception("Git Branch command returned nothing!, is GitLab down?");
}

//Check if the intergration branch is present in the project.
def integrationBranchExists = commandOutPut.contains(INTEGRATION_BRANCH)

//Check if the integrationBranch exists.
if(integrationBranchExists){
	GitLog masterToSprint = new GitLog(MASTER_BRANCH, INTEGRATION_BRANCH, project, PatchRelease)
	outputToHTML("ReleaseNotes_${project}_master", masterToSprint, gitUrl)
}
else{
	println "Integration branch not found. Please check your integrationFlow JSON file."
}

def getProjectName() {
    def jobNameFormat = 'Test_PLATFORM_PreRelease/Release Notes/Generate_Release_Notes_for_'
    def projectName = System.getenv()["JOB_NAME"];
    println "projectName: " + projectName 
    int jobNameLength = jobNameFormat.size()
    return projectName.substring(jobNameLength)
}

//Determine what release type a project is.
//If any JIRA is a story, the Release Type will be a Feature.
//If all JIRAs are a bug, then the Release Type will be a Bug.
def determineReleaseType(jira_list, project_name){
	boolean bug = false
	boolean feature = false
	boolean codeContribution = false

	jira_list.each { jira ->
		JiraAPICall = new Jira(jira, project_name)
		if (JiraAPICall.getJiraIssueType().equals('Story') || JiraAPICall.getJiraIssueType().equals('Epic') || JiraAPICall.getJiraIssueType().equals('Task')){
			feature = true
		}
		if (JiraAPICall.getJiraIssueType().equals('Bug')){
			bug = true
		}
		if (JiraAPICall.getJiraIssueType().equals('Code Contribution ')){
			codeContribution = true                                          
		}
	}
	
	if (feature){
		println "Feature Release"
		return "Feature Release"
	} else if (bug){
		println "Bug Fix Release"
		return "Bug Fix Release"         
	} else if (codeContribution){
		println "Code Contribution"
		return "Code Contribution" 
	} else {
		return "N/A"
	}
}

//Find a list of all the commits for a particular PLATDEV
def findCommitsforPLATDEV(gitLog, PLATDEV){
	def CommitArray = []
    gitLog.getCommits().each { commit ->	
		//Some Commit IDs contain Squashed commit descriptions containing Merged Commits,
		//Only apply the filter to the first line.
		def firstLineDescription
		def trimmedCommitDescription = commit.description.trim()
		trimmedCommitDescription.eachLine { line, count ->
			if (count == 0) {
				firstLineDescription = line
			}
		}

        if (!filterMergeCommits(firstLineDescription)) {
			//Extract the JIRA Issue from the Description and populate the array and commitID map.
            if (commit.description.contains(PLATDEV)){
            	CommitArray.push(commit.commitHash )
            }
        }
    }
    return CommitArray
}

//Ensure that the JIRAs in the list are valid ones and have content in their JSON files.
def validateJIRAs(jira_list, project_name){
	def validated_jira_list = []
	jira_list.each { jira ->
		println "project_name in validateJIRAs:  " + project_name
		JiraAPICall = new Jira(jira, project_name)
		
		//Generate the JIRA json file if it doesn't exist.
		JiraAPICall.generateJIRAFile()
		
		//Determine if the JSON file is valid (present and not empty!)
		if (JiraAPICall.doesJSONFileExist()) {
			validated_jira_list.add(jira)
		} else {
			println jira + " was not found, does this JIRA exist? Skipping!"
		}
	}
	return validated_jira_list
}



def outputToHTML(def outputFileName, def logToOutput, def gitUrl) {
    def writer = new StringWriter()
    def htmlBuilder = new MarkupBuilder(writer)
	def jira_list = []
	def unverified_jira_list = []
	def jiraMap = [:]
	def release_type
	boolean revertedCommits = false
	boolean invalidPlatDevs = false

	//Replace the GitLab Names with the Customer Facing External Names.
	def projectNameMap = [
	  '1AWS_AmadeusWebServices':'Amadeus Web Services',
	  'AirAncillariesManager':'Air Ancillary Manager',	
	  'AutoCore':'Automated Core',
	  'CertifiedSchemas':'Certified Schema',
	  'ContentModule':'Content Module',
	  'ConsoleFramework':'t-Retail Console',
	  'EXPH_Expedia':'Expedia Connector',
	  'GTA_GulliverTravel':'GTA GulliverTravel Connector',
	  'IBE':'t-Retail IBE',
	  'IFG':'IATA Financial Gateway',
	  'OpenJawCommon':'OpenJaw Common',
	  'NDCModule':'NDC Module',
	  'NDCSchema':'NDC Schema',
	  'NDCSolution':'NDC Solution',
	  'Servicing':'Contact Centre Manager',
	  'ServicingSrc':'Contact Centre Manager Source Code',
	  'ServicingSelling':'ServicingSelling',
	  'ServicingAPI':'Servicing API',
	  'ServicingAPISpec':'Servicing API Spec',
	  'sp-oms-api':'Seller Profile OMS API',
	  'sp-console-api':'Seller Profile Console API',
	  'sp-client':'Seller Profile Client',
	  'sp-console':'Seller Profile Console',
	  'tRetailWebApp':'t-Retail Web App',
	  'VTJ_Viator_JSON':'VTJ Viator JSON Connector',
	  'xCar':'Car Manager',
	  'xEvent':'Event Manager',
	  'xHotel':'Hotel Manager',
	  'xHotelMigrator':'Hotel Manager Migrator',
	  'xLocation':'Location Manager',
	  'xLoyalty':'Loyalty Manager',
	  'xProfile':'Profile Manager',
	  'xPromotion':'Marketing Manager',
	  'xPromotionSrc':'Marketing Manager Source Code',
	  'xReport':'Report Manager',
	  'xRez':'t-Retail Web Framework']
	                      
	def externalReleaseNotesSP = [
	  '1AWS_AmadeusWebServices':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/1AWS%20Connector%20Release%20Notes.html',
	  'AirAncillariesManager':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8669',	
	  'AutoCore':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/Automated%20Core%20Release%20Notes.html?csf=1&e=zBFIyD',
	  'CertifiedSchemas':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Certified%20Schema%20Release%20Notes.html',
	  'ConsoleFramework':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8676',
	  'ContentModule':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Content%20Module%20Release%20Notes.html',
	  'DescendantLocationStandalone':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/DescendantLocationStandalone%20Release%20Notes.html?csf=1&e=gLtLsJ',
	  'EXPH_Expedia':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/EXPH_Expedia%20Connector%20Release%20Notes.html?csf=1&e=cZlDR8',
	  'GTA_GulliverTravel':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/GTA_GulliverTravel%20Connector%20Release%20Notes.html?csf=1&e=vOWDYk',
	  'IBE':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8678',
	  'IFG':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/IFG%20Connector%20Release%20Notes.html',
	  'OJDynamicPackaging':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/OJDynamicPackaging%20Release%20Notes.html?csf=1&e=TLaVcu',
	  'OpenJawCommon':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8675',
	  'NDCModule':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/NDC%20Module%20Release%20Notes.html',
	  'NDCSchema':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/NDC%20Schema%20Release%20Notes.html',
	  'NDCSolution':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/NDC%20Solution%20Release%20Notes.html',
	  'sp-oms-api':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Seller%20Profile%20OMS%20API%20Release%20Notes.html',
	  'sp-console-api':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Seller%20Profile%20Console%20API%20Release%20Notes.html',
	  'sp-client':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Seller%20Profile%20Client%20Release%20Notes.html',
	  'sp-console':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Seller%20Profile%20Console%20Release%20Notes.html',
	  'Servicing':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8670',
	  'ServicingSrc':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8671',
	  'ServicingSelling':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/ServicingSelling%20Release%20Notes.html?csf=1&e=g5O8C5',
	  'tRetailAPI':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/t-Retail%20API%20Release%20Notes.html?csf=1&e=8nioye',
	  'LocationAPI':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/Location%20API%20Release%20Notes.html?csf=1&e=8nioye',
	  'tRetailAPISpec':'https://openjawtechweb.sharepoint.com/:u:/r/dev/Released%20Documentation/Release%20Notes/t-Retail%20API%20Release%20Notes.html?csf=1&e=8nioye',
	  'xCar':'https://openjawtechweb.sharepoint.com/dev/Released%20Documentation/Release%20Notes/Car%20Manager%20Release%20Notes.html',
	  'xDistributor':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8674',
	  'xEvent':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8693',
	  'xHotel':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8683',
	  'xHotelMigrator':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8809',
	  'xLocation':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8688',
	  'xLoyalty':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8692',
	  'xPromotion':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8690',
	  'xPromotionSrc':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8682',
	  'xRez':'https://openjawtechweb.sharepoint.com/dev/_layouts/15/DocIdRedir.aspx?ID=Y3322FSZAHSQ-119-8673']
	                      
	def formattedProjectName
	def commitIDMap = [:]
	boolean generateTXTFile = true

    htmlBuilder {
        div(style: "margin-top: 10px; font-size: 14px; padding: 10px; background-color: #fff; border-radius: 10px;") {
            logToOutput.each { gitLog ->
            	
            	//Change to the formatted release names
            	if(projectNameMap[logToOutput.project]) {
    				formattedProjectName = projectNameMap[logToOutput.project]
				} else {
					formattedProjectName = logToOutput.project
				}
                mkp.yieldUnescaped "<a name=\"" + logToOutput.project + "\"></a>"
                
                //Add Sharepoint release notes to the view.
				/*
            	if(externalReleaseNotesSP[logToOutput.project]) {
            		div(style: "float:right") {
	    				a(href:externalReleaseNotesSP[logToOutput.project]){
	    					img(src:'http://dublin.openjawtech.com/images/Sharepoint-icon.png', border:0, style:'height:30px;width:30px;vertical-align: middle;')
							span(formattedProjectName + ' Release Notes')
						}
					}
				}*/

				//Populate the JIRA List.
                gitLog.getCommits().each { commit ->
					
                	//Get the first line of the commit description
                	def trimmedDescription = commit.description.trim()
                	def firstLineDescription
                	
                	trimmedDescription.eachLine { line, count ->
					    if (count == 0) {
					    	firstLineDescription = line
					    }
					}
					
					//Check the commit to see if there is a revert in the description
					if(commit.description.contains("Revert")) {
						println "Reverted Commit detected!"
						revertedCommits = true
					}
                	
                    if (!filterMergeCommits(firstLineDescription)) {
						//Extract the JIRA Issue from the Description and populate the array and commitID map.
		                if(SupportedJIRAList.any { firstLineDescription.contains(it) }){
							if (firstLineDescription.contains('JIRA:')){
							    firstLineDescription = firstLineDescription.substring(firstLineDescription.indexOf('PLATDEV'))
							}  
							if (firstLineDescription.contains(' ')){
								int index_of_space = firstLineDescription.indexOf(' ')
								def jiraIssue = firstLineDescription.substring(0,index_of_space)
								int index_of_dash = jiraIssue.indexOf('-')
								def jiraPrefix = jiraIssue.substring(0,index_of_dash)
								def jiraNumber =  jiraIssue.substring(index_of_dash + 1,jiraIssue.length())
						        if (!jiraNumber.equals('0000')){
						            jira_list.push(jiraIssue.trim())
						        }
							} else {
							    jira_list.push(firstLineDescription.trim())
							}
		                }
                    }
                }
                
                //Remove duplicates from the list
                jira_list = jira_list.unique()
				
				//Remove duplicates from the list
				unverified_jira_list = jira_list
				
				//Ensure that the JIRAs are valid, and appear in Atlassian.
				jira_list = validateJIRAs(jira_list, logToOutput.project)
				
				unverified_jira_list = unverified_jira_list.minus(jira_list)
				
				if(unverified_jira_list) invalidPlatDevs = true

                //Populate the CommitIDMap
                jira_list.each{
                	commitIDMap.put(it,findCommitsforPLATDEV(gitLog, it))
                }
                
                //Detect if there is release notes
                if (jira_list.size() == 0) {
                	println "No need to generate the txt file, no release notes found."
                	generateTXTFile = false
                }

				if (jira_list) {
					//Determine the release type.
                	h2(style: "margin-top: 0px; margin-bottom: 0px;font-family: omnes-pro, deckardregular, Roboto, 'Helvetica Neue', Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji';font-style: normal;font-weight: 400;font-size: 1.5rem;color: #1c96fc;", formattedProjectName +", "+ determineReleaseType(jira_list, logToOutput.project) + " Version _._._")
					
					//If the Project contains reverted commits, then display a warning informing the user that the contents of the release notes
					//may not be fully accurate.
					if(revertedCommits) {
						h3(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ff4d4d;text-align: center;color:white;", "This project contains reverted commits, check which commits have been reverted and remove them from the notes.")
					}
					
					//If the Project contains reverted commits, then display a warning informing the user that the contents of the release notes
					//may not be fully accurate.
					if(invalidPlatDevs) {
						h3(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ff4d4d;text-align: center;color:white;", "This project has PLATDEVs ("+ unverified_jira_list +") that could not be found within Atlassian. Please check that these PLATDEVs are valid.")
					}

					//Iterate over the JIRAs.
					commitIDMap.each{ jira, jiraArray ->
						//Create a new JIRA Class instance.
						JiraAPICall = new Jira(jira, logToOutput.project)
						table(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ccc;") {
							tr {
								td {
									p() {
										if(!JiraAPICall.getJiraEpicLink().equals(null)){
											p(){
												span("Part of Epic:")
												img(src:'http://dublin.openjawtech.com/images/jira/epic.svg', border:0, style:'height:16px;width:16px;vertical-align: middle;')
												a(href:'https://openjawtech.atlassian.net/browse/'+ JiraAPICall.getJiraEpicLink()){
													span(JiraAPICall.getJiraEpicLink())
												}
											}
											hr()
										}
										img(src:JiraAPICall.getJiraIssueIcon(), border:0, style:'height:16px;width:16px;vertical-align: middle;')
										a(href:'https://openjawtech.atlassian.net/browse/'+ jira){
											span(jira)
										}
										span(':')
										span(JiraAPICall.getJiraTitle())
										int CommitNumber = 0
										jiraArray.each{ commit ->
											CommitNumber++
											br('Commit ID: '){
												img(src:'http://dublin.openjawtech.com/images/jira/GitLabIcon.png', border:0, style:'height:16px;width:16px;vertical-align: middle;')
												
												//Create New Class Object.
												GroovyObject gitlabAPICall = new GitlabAPI()
												
												//Determine if the project is in OpenJaw/OpenJaw Connector Group
												def CommitRepo = "OpenJaw"
												if (gitlabAPICall.isOJConnector(logToOutput.project)) CommitRepo = "OpenJaw-Connectors"
												
											    a(href:'http://gitlab.openjawtech.com/'+ CommitRepo +'/' + logToOutput.project + '/commit/' + commit){
													span(commit)
												}
											}
										}
										//If the Project contains several commits for a PLATDEV, then display a warning informing the user that the contents of the release notes
										//may be incorrect.
										if(CommitNumber>2) {
											h3(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ff4d4d;text-align: center;color:white;", jira + " contains "+ CommitNumber +" commits. These commits should have been squashed before merging to the integration branch.")
										}
									}
								}
							}
						}
					}
				}
            }
        }
    }
    //Only Generate the TXT file if there is commits.
	if (generateTXTFile) {
	    PrintWriter pw = new PrintWriter(new File("./${outputFileName}.txt"))
	    pw.write('<!DOCTYPE html>' + "\n")
	    pw.write(writer.toString())
	    pw.close()
    }
}

def filterMergeCommits(def message){
    for(String regex: filters){
        Pattern mergeCommitMessage = Pattern.compile(regex)
        Matcher m = mergeCommitMessage.matcher(message)
        if(m.find()){
            return true
        }
    }
    return false
}

def executeCommand(def commandString){
	def sout = new StringBuffer()
	def serr = new StringBuffer()
	
	def command = commandString.execute()
	command.waitForProcessOutput(sout,serr)
		
	if(serr.toString().length() != 0){
		println "Err: $serr"
	}
	return sout
}
