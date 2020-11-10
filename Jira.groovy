import groovy.json.JsonSlurper

class Jira{

	//Class Variable
	static jiraCounter = 0

    def jiraIssue
    def projectName

    Jira(def jiraIssue, def projectName){
        this.jiraIssue = jiraIssue
        this.projectName = projectName
        //Increment the JIRA Counter
        jiraCounter++
    }

    //Call the API and generate the JSON with the JIRA Reposonse.
    def generateJIRAFile(){
        println "Generating JSON file for : " + this.jiraIssue
        def sout = new StringBuilder()
        def serr = new StringBuilder()
        def apiCall = 'java -jar OAuthTutorialClient-1.0.jar request https://openjawtech.atlassian.net/rest/api/2/issue/' + this.jiraIssue
        def apiCall_exe = apiCall.execute()
        println "ProjectName: " + this.projectName
        def JiraFile = new File(this.projectName + '_' + this.jiraIssue + '.json')
        def strBuf = new StringBuffer()
        apiCall_exe.consumeProcessErrorStream(strBuf)
        JiraFile.text = apiCall_exe.text
    }
    
    //Call the API and generate the JSON with the JIRA Reposonse.
    def generateEPICJIRAFile(Epic){
        println "Generating EPIC JSON file for : " + Epic
        def sout = new StringBuilder()
        def serr = new StringBuilder()
        def apiCall = 'java -jar OAuthTutorialClient-1.0.jar request https://openjawtech.atlassian.net/rest/api/2/issue/' + Epic
        def apiCall_exe = apiCall.execute()
        def JiraFile = new File('Epic_' + Epic + '.json')
        def strBuf = new StringBuffer()
        apiCall_exe.consumeProcessErrorStream(strBuf)
        JiraFile.text = apiCall_exe.text
    }
    
    //Return the JIRA Title for the Epic.
    def getEPICJiraTitle(Epic){
    	println "Generating EPIC TITLE file for : " + Epic
        //Read the file and return the Title.
        def slurper = new JsonSlurper()
        def JIRA_File = new File('Epic_' + Epic + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.summary       
    }
    
    //Delete the JSON file.
    def deleteJIRAFile(){
        println "Deleting JSON file : " + this.jiraIssue
		boolean fileSuccessfullyDeleted =  new File(this.projectName + '_' + this.jiraIssue + '.json').delete() 
    }
    
    //Determine if the JSON file exist and that its not empty.
    boolean doesJSONFileExist(){
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json')
        if (JIRA_File.exists() & JIRA_File.length() > 0){
            return true
        } else {
        	return false
        }
    }

    //Return the JIRA Reporter.
    def getJiraReporter(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Reporter.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.creator.displayName        
    }
    
    //Return the JIRA Title.
    def getJiraTitle(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }

        //Read the file and return the Title.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.summary       
    }
    
    //Return the JIRA Description.
    def getJiraDescription(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Description.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.description       
    }
    
    //Return the JIRA Issue Type.
    def getJiraIssueType(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Type.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.issuetype.name       
    }
    
    //Return the JIRA Issue Icon.
    def getJiraIssueIcon(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Icon.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        
        //Replace the JIRA image URLs with OpenJaw URLs
		def iconIssueMap = ['https://openjawtech.atlassian.net/images/icons/issuetypes/story.svg':'http://dublin.openjawtech.com/images/jira/story.svg',	
	                      'https://openjawtech.atlassian.net/secure/viewavatar?size=xsmall&avatarId=10303&avatarType=issuetype':'http://dublin.openjawtech.com/images/jira/bug.svg',
	                      'https://openjawtech.atlassian.net/images/icons/issuetypes/task.svg':'http://dublin.openjawtech.com/images/jira/task.svg',
	                      'https://openjawtech.atlassian.net/secure/viewavatar?size=xsmall&avatarId=10318&avatarType=issuetype':'http://dublin.openjawtech.com/images/jira/task.svg',
	                      'https://openjawtech.atlassian.net/images/icons/issuetypes/epic.svg':'http://dublin.openjawtech.com/images/jira/epic.svg',
	                      'https://openjawtech.atlassian.net/images/icons/issuetypes/subtask.svg':'http://dublin.openjawtech.com/images/jira/subtask.svg',
	                      'https://openjawtech.atlassian.net/secure/viewavatar?size=xsmall&avatarId=10300&avatarType=issuetype':'http://dublin.openjawtech.com/images/jira/codecontribution.svg']
	                      
	    def iconMatch = iconIssueMap.find{ it.key == JIRA.fields.issuetype.iconUrl }

		if(iconMatch){
		    return iconMatch.value
		} else {
			return 'http://dublin.openjawtech.com/images/jira/jiraIcon.png'
		}
    }
    
    //Return the JIRA Epic Link.
    def getJiraEpicLink(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Link.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.customfield_10001   
    }
    
    //Return the JIRA Status.
    def getJiraStatus(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Status.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.status.name 
    }
    
    
    //Return the JIRA Prefix e.g. PlatDev or OSS.
    def getJiraPrefix(){
        
        //Determine if the JSON file exists. If not, generate it.
        if (!doesJSONFileExist()){
			generateJIRAFile()
        }
        
        //Read the file and return the Status.
        def slurper = new JsonSlurper()
        def JIRA_File = new File(this.projectName + '_' + this.jiraIssue + '.json').text
        def JIRA = slurper.parseText(JIRA_File)
        return JIRA.fields.status.name 
    }
}