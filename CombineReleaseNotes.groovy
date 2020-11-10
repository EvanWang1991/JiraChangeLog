import groovy.xml.MarkupBuilder
import groovy.io.FileType
import ChangeLogEmail
import Jira
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.json.JsonSlurper

ChangeLogEmail master = new ChangeLogEmail();

def dir = new File(".")
dir.eachFile (FileType.FILES) { file ->
	if(file.getPath().endsWith(".txt")){
		if(file.getPath().contains("master")){
			master.addProjectToList(file)
		}
	}
}

println master

//Empty map that will contain the JIRA Issue, and any projects committed against it. E.g. ['PLATDEV-1234',['xDistributor,Servicing']]
def PlatDevMap = [:]

//Define the supported JIRA Names that are supported.
SupportedJIRAList = ['_PLATDEV-', '_OSS-', '_TRASKY-', '_PL-', '_SNP-', '_TNP-', '_PROXTM-', '_AI-', '_ALL-', '_DX-']

//Passing in a PLATDEV, return an array of Projects that have matching commits against it.
def findRelatedProjects(String getPlatDev) {
              
	def Project_List = []
	def JiraFiles = new File(".")
	JiraFiles.eachFile (FileType.FILES) { file ->
		//Find all JSON files and check the name for matches.
		if(file.getPath().endsWith(".json")){
			if(file.getPath().contains(getPlatDev)){
				//Extract the JIRA Issue from the File name.
				def filePathName = file.getPath().toString()
				def removeDir = filePathName.substring(2)
				int index_of_underscore = removeDir.lastIndexOf('_')
				int index_of_dot = removeDir.indexOf('.')
				def getProjectName = removeDir.substring(0,index_of_underscore)
				//Add it the project name to the array.
				Project_List.add(getProjectName)
			}
		}
	}
	//Remove Duplicate PLATDEVs from the Epic.
	Project_List = Project_List.unique()

	//Sort the PLATDEVs from the Epic.
	Project_List = Project_List.sort()

	return Project_List
}

//Passing in the EPIC Platdev, Search the available JSON files and determine if any of them are part of the Epic.
def findRelatedPLATDEVsinEpic(String Epic) {
	def PLATDEV_List = []
	def JiraFiles = new File(".")
	JiraFiles.eachFile (FileType.FILES) { file ->
		if(file.getPath().endsWith(".json")){
			//Read the file and return the Link.
			def slurper = new JsonSlurper()
			def filePathName = file.getPath().toString()
			def JIRA_File = new File(filePathName).text
			if(JIRA_File){
				def JIRA = slurper.parseText(JIRA_File)
				//Check if the customfield matches the Epic Platdev.
				if(JIRA.fields.customfield_10001.equals(Epic)){
					PLATDEV_List.push(JIRA.key)
				}
			}
		}
	}
	//Remove Duplicate PLATDEVs from the Epic.
	PLATDEV_List = PLATDEV_List.unique()

	//Sort the PLATDEVs from the Epic.
	PLATDEV_List = PLATDEV_List.sort()

	return PLATDEV_List
}

//For the release note generation, a JSON file is generated containing the Response from the JIRA API.
//This method populates a map with a JIRA issue number and any associated projects.
def JiraFiles = new File(".")

JiraFiles.eachFile (FileType.FILES) { file ->
	if(file.getPath().endsWith(".json")){
		if(SupportedJIRAList.any { file.getPath().contains(it) }){
			if (!file.getPath().contains("Epic")) {
                                                       println "File Path: " + file.getPath()
				//Extract the JIRA Issue from the File name.
				def filePathName = file.getPath().toString()
				def removeDir = filePathName.substring(2)
				int index_of_underscore = removeDir.lastIndexOf('_')
				int index_of_dot = removeDir.indexOf('.')
				def getProjectName = removeDir.substring(0,index_of_underscore)
				def getPlatDev = removeDir.substring(index_of_underscore + 1,index_of_dot)
				if(validateJIRAs(getPlatDev, getProjectName)) PlatDevMap.put(getPlatDev, findRelatedProjects(getPlatDev))
			}
		}
	}
}

//Ensure that the JIRAs in the list are valid ones and have content in their JSON files.
def validateJIRAs(jira, project_name){
	JiraAPICall = new Jira(jira, project_name)

	//Determine if the JSON file is valid (present and not empty!)
	if (JiraAPICall.doesJSONFileExist()) {
		return true
	} else {
		println jira + " was not found, does this JIRA exist? Skipping!"
		return false
	}
}

def masterListOfProjects = master.getListOfProjects()
def masterListOfProjectNames = master.parseReleaseProjectName()

//Generate the HTML Page
createHtmlPage(masterListOfProjects, masterListOfProjectNames, "release_notes.html", PlatDevMap)


def createHtmlPage(def projectFiles, def projectNames, def outputFileName , Map PlatDevMap){
	def writer = new StringWriter()
	def htmlBuilder = new MarkupBuilder(writer)

	//Empty map that will contain the EPIC JIRA Issue, and any Platdevs committed against it. E.g. ['PLATDEV-1234',['PLATDEV-1235,PLATDEV-1236']]
	def epicMap = [:]
	def noReleaseNotes = false

	//Replace the GitLab Names with the Customer Facing External Names.
	def projectNameMap = ['AirAncillariesManager':'Air Ancillary Manager',
		'AutoCore':'Automated Core',
		'CertifiedSchemas':'Certified Schemas',
		'ConsoleFramework':'t-Retail Console',
		'IBE':'t-Retail IBE',
		'OpenJawCommon':'OpenJaw Common',
		'Servicing':'Contact Centre Manager',
		'ServicingSrc':'Servicing Source Code',
		'xCar':'Car Manager',
		'xEvent':'Event Manager',
		'xHotel':'Hotel Manager',
		'xHotelMigrator':'Hotel Manager Migrator',
		'xLocation':'Location Manager',
		'xLoyalty':'Location Manager',
		'xProfile':'Profile Manager',
		'xPromotion':'Acquisition  Manager',
		'xPromotionSrc':'Acquisition Manager Source Code',
		'xReport':'Report Manager',
		'xRez':'t-Retail Web Framework']

	//*********************************************************************************************************
	//Release notes generation is generated in two views, Broken down by JIRA & Project Views.
	//*********************************************************************************************************
	def formattedProjectName
	htmlBuilder.html('lang' : 'en-ie') {
		head(){
			meta('http-equiv' : 'Content-Type', 'content' : 'text/html; charset=UTF-8')
		}
		body() {
			//***********************************
			//EMAIL HEADER
			//***********************************
			div(style: """-webkit-border-radius: 6px; -moz-border-radius: 6px; border-radius: 6px; font-size: 14px; padding: 6px; border: 1px solid #ccc; width: 1080px;
            background-image: linear-gradient(#250576, #24065b, #24054f, #25043c); margin: 0 auto;"""){
				//Header Logo and Header Text
				
				def Team = System.getenv()['team']
				def ReleaseNumber = System.getenv()['BUILD_DISPLAY_NAME']
				
				div(style:"height:260px"){
					img(src:"http://dublin.openjawtech.com/images/test_report/tRetailIcon.gif", align:"right")
					span(style:"font-size: 40px;color:white;font-family: Arial, Helvetica, sans-serif;font-weight: lighter;padding-top:50px;padding-left:40px;float: left;clear: left", "Release Notes")
					span(style:"font-size: 20px;color:white;font-family: Arial, Helvetica, sans-serif;font-weight: lighter;padding-left:40px;float: left;clear: left", Team + " t-Retail Platform Release Notes " + ReleaseNumber)
				}
				
				//*********************************************************************************************************
				//Display Message if no changes were found in the integration branches.
				//*********************************************************************************************************
				if (!projectNames){
					
					//Boolean to determine if there is release notes to display.
					noReleaseNotes = true
					
					div(style: "margin-top: 10px; font-size: 14px; padding: 20px; background-color: #fff; border-radius: 10px;"){
						div{
							h2(style:'display: inline;'){
								span("No Release Notes Found.")
							}
						}
						table(style: "border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ccc;") {
							tr {
								td {
									p() {
										span('No new changes have been detected on your integration branches. Please commit and try again.')
									}
								}
							}
						}
					}
				}
				
				//***********************************
				//JIRA Breakdown.
				//***********************************
				if (!noReleaseNotes){
					div(style: """-webkit-border-radius: 6px; -moz-border-radius: 6px; border-radius: 6px; font-size: 14px; width: 1080px;margin: 0 auto;"""){
						div(style:"""border-radius: 10px; width: 100%; margin: 5px 0 5px 0%; transition: height 0.5s;
									 -webkit-transition: height 0.5s; text-align: left; overflow: hidden; background-color: #551aa8"""){
							h2(style: "font-size: 1.6rem; font-weight: 400; color: white; text-align: center;","JIRA Breakdown")
							h3(style: "font-size: 1.0rem;font-weight: 400;color: white;text-align: center;","The JIRA breakdown organises the release notes by JIRA issue and lists any projects with commits against this issue.")

							//Generate a div that includes a list of all the PLATDEVs to be included in this release.
							//Each PLATDEVs is a link to the specific section of the release notes.
							div(style: "background-color: #551aa8;padding: 5px 5px 5px 5px;border-radius: 6px;width: 99%;margin: -10px 1% 5px 0%;float: left;position: relative;transition: height 0.5s;-webkit-transition: height 0.5s;text-align: center;overflow: hidden;"){
								def epicArray = []
								PlatDevMap.each{ PlatDev, Related_Project_Array ->
									//Determine if the PLATDEV is apart of an epic, by looking at JiraAPICall.getJiraEpicLink()
									JiraAPICall = new Jira(PlatDev, Related_Project_Array[0])
									if (JiraAPICall.getJiraEpicLink().equals(null)){
										div(style: "min-width: 210px;padding-left: 5px;padding-right: 5px; float: left;background-color: #25043c;border-radius: 10px; text-align: center; line-height:25px;margin-left:3px;margin-bottom:3px"){
											img(src:JiraAPICall.getJiraIssueIcon(), border:0, style:'height:16px;width:16px;margin-bottom:-3px;')
											a(href: "#${PlatDev}", style: "text-decoration: none; color:white"){
												span("${PlatDev}")
											}
										}
									} else if(!epicArray.contains(JiraAPICall.getJiraEpicLink())){
										div(style: "width: 210px; float: left;background-color: #25043c;border-radius: 10px; text-align: center; line-height:25px;;margin-left:3px;margin-bottom:3px"){
											img(src:'http://dublin.openjawtech.com/images/jira/epic.svg', border:0, style: 'vertical-align: middle;')
											a(href: "#${JiraAPICall.getJiraEpicLink()}", style: "text-decoration: none; color:white"){
												span(JiraAPICall.getJiraEpicLink())
												//Query the JIRA API and generate a JSON file for the EPIC.
												JiraAPICall.generateEPICJIRAFile(JiraAPICall.getJiraEpicLink())
											}
										}
										//Add the Epic to an array, array is used to stop the Epic from appearing multiple times.
										epicArray.push(JiraAPICall.getJiraEpicLink())
										epicMap.put(JiraAPICall.getJiraEpicLink(), findRelatedPLATDEVsinEpic(JiraAPICall.getJiraEpicLink()))
									}
								}
							}
						}
						//For each Epic discovered, create a section for it in the release notes.
						epicMap.each{ epic_PLATDEV, Related_Project_Array ->
							a(name: "${epic_PLATDEV}","")
							div(style: "margin-top: 10px; font-size: 14px; padding: 10px; background-color: #fff; border-radius: 10px;"){
								//Epic Platdev and Title
								div(style:'margin-bottom: 15px;'){
									img(src:'http://dublin.openjawtech.com/images/jira/epic.svg', border:0, style:'height:16px;width:16px;margin-bottom:-3px;')
									a(href:'https://openjawtech.atlassian.net/browse/'+ epic_PLATDEV){
										h2(style:'display: inline;'){
											span(style: "font-family: omnes-pro, deckardregular, Roboto, 'Helvetica Neue', Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji';font-style: normal;font-weight: 400;font-size: 1.5rem;color: #1c96fc;", epic_PLATDEV + " " + JiraAPICall.getEPICJiraTitle(epic_PLATDEV))
										}
									}
								}
								Related_Project_Array.each{ platdev ->
									def matching_platdev = PlatDevMap.find{ it.key == platdev }
									def project_array = []
									project_array = matching_platdev.value
									JiraAPICall = new Jira(platdev, project_array[0])
									div{
										img(src:JiraAPICall.getJiraIssueIcon(), border:0, style:'height:16px;width:16px;margin-bottom:-3px;')
										a(href:'https://openjawtech.atlassian.net/browse/'+ platdev){
											h3(style:'display: inline;'){
												span(style: "font-family: omnes-pro, deckardregular, Roboto, 'Helvetica Neue', Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji';font-style: normal;font-weight: 400;font-size: 1.0rem;color: #1c96fc;","${platdev}"  + " " + JiraAPICall.getJiraTitle())
											}
										}
									}
									table(style:"border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin-bottom: 15px; background-color:#ccc;"){
										tr{
											td{
												p{
													ul{
														project_array.each{ project_it ->
															li(style: "margin-bottom: 5px;"){
																a(href: "#${project_it}", style: "text-decoration:none"){
																	span("${project_it}")
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
						}
						//Project Breakdown for each JIRA Issue.
						PlatDevMap.each{ PlatDev, Related_Project_Array ->
							JiraAPICall = new Jira(PlatDev, Related_Project_Array[0])
							if (JiraAPICall.getJiraEpicLink().equals(null)){
								a(name: "${PlatDev}","")
								div(style: "margin-top: 10px; font-size: 14px; padding: 10px; background-color: #fff; border-radius: 10px;"){
									div{
										img(src:JiraAPICall.getJiraIssueIcon(), border:0, style:'height:16px;width:16px;margin-bottom: -3px;')
										a(href:'https://openjawtech.atlassian.net/browse/'+ PlatDev){
											h2(style:'display: inline;'){
												span("${PlatDev}"  + " " + JiraAPICall.getJiraTitle())
											}
										}
									}
									table(style:"border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ccc;"){
										tr{
											td{
												p{
													ul{
														Related_Project_Array.each{ project ->
															li(style: "margin-bottom: 5px;"){
																a(href: "#${project}", style: "text-decoration:none"){
																	span("${project}")
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
						}
					}
				}
				//***********************************
				//Project Breakdown.
				//***********************************
				if (!noReleaseNotes){
					//Generate a div that includes a list of all the Projects to be included in this release.
					//Each Project is a link to the specific section of the release notes.
					div(style:"""border-radius: 10px; width: 100%; margin: 5px 0 5px 0%; transition: height 0.5s;-webkit-transition: height 0.5s; text-align: left; overflow: hidden; background-color: #551aa8"""){
						h2(style: "font-size: 1.6rem; font-weight: 400; color: white; text-align: center;","Project Breakdown")
						h3(style: "font-size: 1.0rem;font-weight: 400;color: white;text-align: center;","The Project breakdown organises the release notes by Project and lists any JIRA issues committed in that project.")
						div(style: "background-color: #551aa8;padding: 5px 5px 5px 5px;border-radius: 6px;width: 99%;margin: -10px 1% 5px 0%;float: left;position: relative;transition: height 0.5s;-webkit-transition: height 0.5s;text-align: center;overflow: hidden;"){
	
							//Convert the internal names of the projects, to the external ones.
							projectNames.each{ project ->
								if(projectNameMap[project]) {
									formattedProjectName = projectNameMap[project]
								} else {
									formattedProjectName = project
								}
								//Div containing the link and external name.
								div(style: "min-width: 200px;padding-left: 5px;padding-right: 5px;float: left;background-color: #25043c;border-radius: 10px; color: #00b300; text-align: center; height:25px;line-height:25px;margin-left:3px;margin-bottom:3px"){
									a(href: "#${project}", style: "text-decoration: none; color:white"){
										span("${formattedProjectName}")
									}
								}
							}
						}
					}
					//Project Breakdown.
					//During release notes generation, HTML code is generated and stored in txt files, apply this HTML to this template. Combining it all together.
					projectFiles.each{
						def htmlLine = it.readLines()
						htmlLine.each{
							mkp.yieldUnescaped it.replaceAll("<!DOCTYPE html>", "").replaceAll("<call>", "").replaceAll("</call>", "")
						}
					}
				}
			}
		}
	}

	PrintWriter pw = new PrintWriter(new File(outputFileName))
	pw.write('<!DOCTYPE html>' + "\n")
	pw.write(writer.toString())
	pw.close()
}
