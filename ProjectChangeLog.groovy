import groovy.transform.Field
import groovy.xml.MarkupBuilder
import hudson.model.*
import GitLog

import java.util.regex.Matcher
import java.util.regex.Pattern

@Field final filters = ["\\b.*Merge remote-tracking branch 'origin/master'.*\\b"]
@Field final gitUrl = "http://git@gitlab.openjawtech.com/"
@Field final team_repo = System.getenv()['team_repo']
@Field final workspace = System.getenv()['WORKSPACE']


def project = getProjectName()

final String MASTER_BRANCH = "master"
final String INTEGRATION_BRANCH = args[0]

def commandOutPut = executeCommand("git branch -a")
def integrationBranchExists = commandOutPut.contains(INTEGRATION_BRANCH)

if(integrationBranchExists){
	GitLog masterToSprint = new GitLog(MASTER_BRANCH, INTEGRATION_BRANCH, project, "false")
	outputToHTML("ChangeLog_${project}_master", masterToSprint, gitUrl)
	
	GitLog sprintToMaster = new GitLog(INTEGRATION_BRANCH, MASTER_BRANCH, project, "false")
	outputToHTML("ChangeLog_${project}_integration", sprintToMaster, gitUrl)
	
	processFeatures(args, INTEGRATION_BRANCH)
}
else{
	println "Integration branch not found. Feature branches will be compared to the master branch"
	processFeatures(args, MASTER_BRANCH)
}

def processFeatures(def args, def branchToCompareTo){
	for (int index = 1; index < args.length; index++) {
		def repoBranch = args[index]
		if (!repoBranch.contains("/")) {
			platdev = repoBranch
			repoBranch = "${team_repo}/" + repoBranch
		}
		
		def platdev = repoBranch.split('/')[1]
		GitLog gitlog = new GitLog(branchToCompareTo, repoBranch, getProjectName(), "false")
		if(branchToCompareTo.equals( team_repo+ "/master")){
			outputToHTML("ChangeLog_${getProjectName()}_NoIntegrationBranch${platdev}", gitlog, gitUrl)
		}
		else{
			outputToHTML("ChangeLog_${getProjectName()}_feature${platdev}", gitlog, gitUrl)
		}
	}
}

def getProjectName() {
	Pattern projectNamePattern = Pattern.compile("Current_Changes_in_(.*)")
	Matcher m = projectNamePattern .matcher(System.getenv()["JOB_NAME"])
	if(m.find()){
    		return m.group(1)
	}
}


def outputToHTML(def outputFileName, def logToOutput, def gitUrl) {
    def writer = new StringWriter()
    def htmlBuilder = new MarkupBuilder(writer)

    htmlBuilder {
        div(style: """margin-top: 10px; font-size: 14px; padding: 20px; background-color: #fff; border-radius: 10px;""") {
            logToOutput.each { gitLog ->
                mkp.yieldUnescaped "<a name=\""+ logToOutput.project + "\"></a>"
                h2(style:"font-style: normal;font-weight: 400;font-size: 1.6rem;color: #1c96fc;text-align: center;",logToOutput.project)
                h2(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: #1c96fc;text-align: center;",gitLog.getLogHeader())
                if (gitLog.getCommits().size() == 0) {
                    table(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 30px auto; background-color:#ccc;") {
                        tr {
                            td {
                                span("Move along, nothing to see here")
                            }
                        }
                    }
                }

                gitLog.getCommits().each { commit ->
                    if (!filterMergeCommits(commit.description)) {
                        table(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%;margin: 30px auto; background-color:#ccc;") {
                            tr {
                                td {
									br('Commit ID: '){
										img(src:'http://dublin.openjawtech.com/images/jira/GitLabIcon.png', border:0, style:'height:16px;width:16px;vertical-align: middle;')
										a(href: "${commit.buildCommitUrl(gitUrl, gitLog.destBranch, logToOutput.project)}"){
											span(commit.commitHash + "\n")
										}
									}
                                    br()
                                    if (commit.merge != null) {
                                        span("$commit.merge")
                                        br()
                                    }
                                    span("${commit.commitAuthor}")
                                    br()
                                    span("${commit.commitDate}")
                                    br()
                                    div(style: "padding-left: 50px") {
                                        commit.description.readLines().each {
                                            mkp.yield "\t" + it
                                            mkp.yieldUnescaped '<br>'
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

    PrintWriter pw = new PrintWriter(new File("./${outputFileName}.txt"))
    pw.write('<!DOCTYPE html>' + "\n")
    pw.write(writer.toString())
    pw.close()
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
	command.consumeProcessOutput(sout,serr)
	command.waitFor()
		
	if(serr.toString().length() != 0){
		println "Err: $serr"
	}
	return sout
}
