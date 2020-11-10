import groovy.util.XmlParser
import groovy.util.XmlSlurper
import groovy.io.FileType
import groovy.xml.MarkupBuilder
import java.text.SimpleDateFormat
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import groovy.json.*

class GenerateDependencyReport{
	static void main(String... args) {
		
		//Create New Class Object.
		GroovyObject Utilities = new ReportUtilities();
		GroovyObject HTMLUtilities = new HTMLUtilities();
		GroovyObject ConfluenceUtilities = new ConfluenceUtilities();
		
		//Jenkins Variables
		String uploadToConfluence = args[0]
		def currentSuiteRelease = args[1]
		def WorkSpaceDirectory = System.getenv()['WORKSPACE']
		def ReportDirectory = WorkSpaceDirectory + "/build/reports"
		
		//Gather a list of all XML files generated from the report.
		def ArrayofXMLReports = Utilities.findAllXMLReportFiles(ReportDirectory);
	
		//What XML files do we include in the generated report.
		def FilesToScan = ["openjaw-SuiteIntegration-tRetailAPI.xml",
			               "openjaw-SuiteIntegration-tRetailWebApp.xml",
						   "openjaw-SuiteIntegration-ndc.xml",
						   "openjaw-SuiteIntegration-servicingAPI.xml",
						   "openjaw-SuiteIntegration-locationAPI.xml",
						   "openjaw-SuiteIntegration-landingPagesAPI.xml",
						   "openjaw-SuiteIntegration-pricesAPI.xml",
						   "openjaw-SuiteIntegration-cm-api.xml",
						   "openjaw-SuiteIntegration-sp-console.xml",
						   "openjaw-SuiteIntegration-sp-console-api.xml",
						   "openjaw-SuiteIntegration-sp-oms-api.xml",
						   "openjaw-SuiteIntegration-staticContentAPI.xml",
						   "openjaw-SuiteIntegration-editor.xml",
						   "openjaw-SuiteIntegration-services.xml"]
		
		//Gather the XML and generate the Reports
		Utilities.getXMLFromReport(FilesToScan, ArrayofXMLReports, ReportDirectory);
		
		//Gather the Generated Reports.
		def ArrayofTXTReports = Utilities.findAllGeneratedFiles(WorkSpaceDirectory);
		
		//Create the HTML and add the reports
		HTMLUtilities.createHtmlPage(ArrayofTXTReports, WorkSpaceDirectory, uploadToConfluence, currentSuiteRelease);
		
		//Upload the report to Confluence
		if(uploadToConfluence.equals("true")) ConfluenceUtilities.uploadToConfluence(ConfluenceUtilities.generateJSON(ArrayofTXTReports, WorkSpaceDirectory, currentSuiteRelease))
	}
}

class ReportUtilities{
	
	//The Report Ant Task Generates a number of XML files.
	//Find them and add them to an Array.
	def findAllXMLReportFiles(def Directory) {
		def XMLFiles = new File(Directory)
		def DirectoryLength = Directory.length()
		def DependencyXMLReports = []
		//Iterate over the XML reports
		XMLFiles.eachFile (FileType.FILES) { file ->
			if(file.getPath().endsWith(".xml")){
				def filePathName = file.getPath().toString()
				def fileNameOnly = filePathName.substring(DirectoryLength + 1)
				DependencyXMLReports.add(0, fileNameOnly)
			}
		}
		return DependencyXMLReports
	}
	
	
	def findAllGeneratedFiles(def Directory) {
		def GeneratedFiles = new File(Directory)
		def DirectoryLength = Directory.length()
		def DependencyTXTReports = []
		//Iterate over the XML reports
		GeneratedFiles.eachFile (FileType.FILES) { file ->
			if(file.getPath().contains("Dependency-") && file.getPath().endsWith(".txt")){
				def filePathName = file.getPath().toString()
				def fileNameOnly = filePathName.substring(DirectoryLength + 1)
				//Append the file names to an array.
				if(!fileNameOnly.equals("Dependency-openjaw-SuiteIntegration-editor.txt") || !fileNameOnly.equals("Dependency-openjaw-SuiteIntegration-services.txt")) DependencyTXTReports.add(fileNameOnly)
			}
		}

		//Add these two files to the end of the array.
		DependencyTXTReports.add("Dependency-openjaw-SuiteIntegration-editor.txt")
		DependencyTXTReports.add("Dependency-openjaw-SuiteIntegration-services.txt")		
		return DependencyTXTReports.unique()
	}

	//Get the contents of the XML file and Parse out the contents.
	def getXMLFromReport(def FilesToScan, def DependencyXMLReports, def ReportDirectory) {
	FilesToScan.each { Report ->
		if (DependencyXMLReports.contains(Report)){
			def fileContents = new File(ReportDirectory + '/' + Report)			
			def doc = new XmlSlurper().parse(fileContents);
			def ReportFileName = Report.substring(0, Report.length() - 4)
			def textFile = "Dependency-" + ReportFileName + ".txt"
			def file = new File(textFile)
			file.createNewFile()
			
			//Looping over each Module
			doc.dependencies.module.each { module ->
				//Only search for the OpenJaw Dependencies
				String moduleOrganisation = module.@organisation
				if(moduleOrganisation.contains("openjaw")) {
					//Write the dependencies to a txt file.
					writeToFile(module.@name, module.revision[0].@name, module.revision[0].@status, getPublishedDate(module.revision[0].@pubdate.toString()), ReportDirectory, textFile)
				}
			}
			sortTextFile(textFile);
			}
			else {
				println "Report:" + Report + " not found in Directory"
			}
		}
	}

	//Format the Published Date into human readable format.
	def getPublishedDate(def UnformattedDate){
		Date date = Date.parse( 'yyyyMMddhhmmSS', UnformattedDate )
		String newDate = date.format( 'dd/MM/yyyy hh:mm:SS' )
		return newDate
	}

	//Write the dependencies into a txt file for easy processing.
	def writeToFile(def Name, def Release, def Status, def Date, def ReportDirectory, def ReportFile){
		
		def file = new File(ReportFile)		
		def trimmedName = Name.toString().trim()

		//Create the file if it doesn't exist. Otherwise right the contents to the file.
		file << getFormattedName(trimmedName) + "_" +Release+ "_" +Status+ "_" +Date+ "\n"
	}
	
	def getFormattedName (def unformattedName) {
		String formattedProjectName
		//Replace the Ivy Names with the Customer Facing External Names.
		def projectNameMap = [
		  'airancillariesmanager':'AirAncillariesManager',
		  'axis':'Axis',
		  'certifiedschemas':'Certified Schemas',
		  'console':'Console Framework',
		  'EXPH_Expedia':'Expedia Connector',
		  'GTA_GulliverTravel':'GTA GulliverTravel Connector',
		  'ibe':'IBE',
		  'commons':'OpenJawCommon',
		  'ojdp':'OpenJawDynamicPackaging',
		  'servicing':'Servicing',
		  'servicingSrc':'ServicingSrc',
		  'ServicingSelling':'ServicingSelling',
		  'descendantlocation':'Descendant Location',
		  'xcar':'xCar',
		  'xevent':'xEvent',
		  'xhotel':'xHotel',
		  'tRetailWebApp':'tRetailWebApp',
		  'openjaw-api-spec':'tRetail API Spec',
		  'openjaw-servicing-selling':'Servicing Selling',
		  'openjaw-servicing-selling-custom':'Servicing Selling Custom',
		  'openjaw-servicing-selling-model':'Servicing Selling Model',
		  'xlocation':'xLocation',
		  'xloyalty':'xLoyalty',
		  'xprofile':'xProfile',
		  'xpromotion':'xPromotion',
		  'xPromotionSrc':'xPromotionSrc',
		  'xdistributor':'xDistributor',
		  'xrez':'xRez',
		  'xmltest':'XMLTest',
		  'guitest':'GUITest',
		  'jsontest':'JSONTest']
		
		//Change to the formatted release names
		if(projectNameMap.containsKey(unformattedName)) {
			formattedProjectName = projectNameMap[unformattedName]
		} else {
			formattedProjectName = unformattedName
		}
		return formattedProjectName
	}

	//Read the generated text file, line by line.
	def readTextFile(def ReportDirectory, def FileName){
		def file = new File(ReportDirectory + "/" + FileName)
		def RevisionList = [] 
		def lineNo = 1
		def line
		file.withReader { reader ->
			while ((line = reader.readLine())!=null) {
				RevisionList.add(line)
				lineNo++
			}
		}
		return RevisionList
	}
	
	//Read the generated text file, line by line.
	def sortTextFile(FileName){
		String inputFile = FileName;
		String outputFile = FileName;

		FileReader fileReader = new FileReader(inputFile);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String inputLine;
		List<String> lineList = new ArrayList<String>();
		while ((inputLine = bufferedReader.readLine()) != null) {
			lineList.add(inputLine);
		}
		fileReader.close();

		Collections.sort(lineList);

		FileWriter fileWriter = new FileWriter(outputFile);
		PrintWriter out = new PrintWriter(fileWriter);
		for (String outputLine : lineList) {
			out.println(outputLine);
		}
		out.flush();
		out.close();
		fileWriter.close();
	}
	
	def formatFileName(def FileName) {
		def FrontTextLength = "Dependency-openjaw-SuiteIntegration-".length()
		//Remove the .xml extension
		def RemoveXMLExtention = FileName.substring(0, FileName.length() - 4)
		def Unformatted = RemoveXMLExtention.substring(FrontTextLength)

		String formattedFileName
		//Replace the Ivy Names with the Customer Facing External Names.
		def FileNameMap = [
		  'ndc':'NDC',
		  'Openjaw-servicing-selling':'Servicing Selling',
		  'cm-api':'Content Module API',
		  'sp-console':'Seller Profile Console',
		  'sp-console-api':'Seller Profile Console API',
		  'sp-oms-api':'Seller Profile OMS API',
		]
		
		//Change to the formatted release names
		if(FileNameMap.containsKey(Unformatted)) {
			formattedFileName = FileNameMap[Unformatted]
		} else {
			formattedFileName = Unformatted
		}
		
		if (!formattedFileName.equals("tRetailAPI") && !formattedFileName.equals("tRetailWebApp")) {
			formattedFileName = formattedFileName.capitalize()
		}
		
		return formattedFileName
	}
	
	//Get the Date Time
	def getDateTime () {
		def date = new Date()
		def sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
		return sdf.format(date).toString()
	}
}

class HTMLUtilities{
	
	GroovyObject Utilities = new ReportUtilities();
	
	//Generate the HTML Page used to send in the email.
	def createHtmlPage(def DependencyList, def WorkSpaceDirectory, String uploadToConfluenceHeader, def currentSuiteRelease){
		def writer = new StringWriter()
		def htmlBuilder = new MarkupBuilder(writer)

		htmlBuilder.html('lang' : 'en-ie') {
			head(){
				meta('http-equiv' : 'Content-Type', 'content' : 'text/html; charset=UTF-8')
			}
			body() {
				//***********************************
				//Project Breakdown.
				//***********************************
				div(style: """-webkit-border-radius: 6px; -moz-border-radius: 6px; border-radius: 6px; font-size: 14px; padding: 6px; border: 1px solid #ccc; width: 1080px;
            background-image: linear-gradient(#250576, #24065b, #24054f, #25043c); margin: 0 auto;"""){
					//Header Logo and Header Text
					div(style:"height:260px"){
						def emailHeader
						if (currentSuiteRelease) {
							emailHeader = currentSuiteRelease + " Dependency Report"
						} else {
							emailHeader = "Dependency Report"
						}
						img(src:"http://dublin.openjawtech.com/images/test_report/tRetailIcon.gif", align:"right")
						span(style:"font-size: 40px;color:white;font-family: Arial, Helvetica, sans-serif;font-weight: lighter;padding-top:50px;padding-left:40px;float: left;clear: left", emailHeader)
					}		
					
					//Generate a div that includes a list of all the Projects to be included in this release.
					//Each Project is a link to the specific section of the release notes.
					div(style:"""border-radius: 10px; width: 100%; margin: 5px 0 5px 0%; transition: height 0.5s; -webkit-transition: height 0.5s; text-align: left; overflow: hidden; background-color: #1c96fc"""){
						h2(style: "font-size: 1.6rem; font-weight: 400; color: white; text-align: center;","Ivy & Nexus Dependency Report")
						h3(style: "font-size: 1.0rem;font-weight: 400;color: white;text-align: center;","Below is the known dependencies that exist between each product as of " + Utilities.getDateTime())
						if (uploadToConfluenceHeader.equals("true")) { 
							h3(style: "font-size: 1.0rem;font-weight: 400;color: white;text-align: center;","This report has been uploaded to the Confluence"){
								a(href: "https://openjawtech.atlassian.net/wiki/spaces/DEPEND"){
									span(style: "margin-top: 0px; margin-bottom: 0px;font-family: omnes-pro, deckardregular, Roboto, 'Helvetica Neue', Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji';font-style: normal;font-weight: 400;font-size: 1.2rem;color: #551aa8;","Dependency Report Page")
								}
							}
						}
					}

					div(style: "margin-top: 10px; font-size: 14px; padding: 10px; background-color: #fff; border-radius: 10px;") {
						
						DependencyList.each{ file ->
							h2(style: "margin-top: 0px; margin-bottom: 0px;font-family: omnes-pro, deckardregular, Roboto, 'Helvetica Neue', Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol', 'Noto Color Emoji';font-style: normal;font-weight: 400;font-size: 1.5rem;color: #1c96fc;", Utilities.formatFileName(file))
							//During release notes generation, HTML code is generated and stored in txt files, apply this HTML to this template. Combining it all together.
							table(style: "width:99%") {
								tr {
									td {
										def RevisionList = Utilities.readTextFile(WorkSpaceDirectory, file)
										
										//Check if there is dependencies
										if(!RevisionList.isEmpty()) {
											RevisionList.each{  RevisionString ->
												
												def RevisionArray = RevisionString.split('_')
												
												def ProjectName = RevisionArray[0]
												def ProjectVersion = RevisionArray[1]
												def ProjectStatus = RevisionArray[2]
												def DateReleased = "Date Released:"
												if(ProjectStatus.contentEquals("release")) ProjectStatus = "Released"
												if(ProjectStatus.contentEquals("integration")) {
													ProjectStatus = "Integration"
													DateReleased = "Date Published:"
												}
												
												def ProjectDate = RevisionArray[3]
												
												div(style: "height:40px; line-height:40px; padding-left: 15px;display: inline-block; border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ccc;", it) {
													span(style: "width: 25%;display: inline-block;","Project:" + ProjectName)
													if(ProjectStatus.equals("Released")) {
														span(style: "width: 15%;display: inline-block;","")
													} else {
														span(style: "width: 15%;display: inline-block;","Status:" + ProjectStatus)
													}
													span(style: "width: 25%;display: inline-block; font-weight: bold;","Version:" + ProjectVersion)
													span(style: "width: 25%;display: inline-block;",DateReleased + ProjectDate)
												}
												br()
											}
										} else {
											div(style: "height:40px; line-height:40px; padding-left: 15px;display: inline-block; border-radius: 10px; border-spacing: 0.5rem; width: 100%; margin: 5px auto; background-color:#ccc;", it) {
												span(style: "width: 25%;display: inline-block;","Project:No Dependency Found")
												span(style: "width: 15%;display: inline-block;","Status: ---")
												span(style: "width: 25%;display: inline-block; font-weight: bold;","Version: ---")
												span(style: "width: 25%;display: inline-block;","Date Released: ---")
											}
											br()
										}
									}
								}
							}
						}
						hr()
					}
				}
			}
		}

		PrintWriter pw = new PrintWriter(new File("DependencyReport.html"))
		pw.write('<!DOCTYPE html>' + "\n")
		pw.write(writer.toString())
		pw.close()
	}
}

class ConfluenceUtilities{
	
	GroovyObject Utilities = new ReportUtilities();

	def generateJSON(def DependencyList, def WorkSpaceDirectory, def currentSuiteRelease){
		
		//Variable for the Base JSON
		def baseJSON
		
		//Variable for the HTML Tables in the files.
		def baseHTMLTable
		
		//Generate the parent page first.
		generateParentPage()
		
		//For each Dependency File.
		DependencyList.each{ file ->
			//Variable for the table rows in a file.
			def baseTableRow

			//For Each line in a single file.
			def RevisionList = Utilities.readTextFile(WorkSpaceDirectory, file)
			
			//Check if there is dependencies
			if(!RevisionList.isEmpty()) {
				RevisionList.each{  RevisionString ->
					//Split each line in the txt file and assign variables to them.
					def RevisionArray = RevisionString.split('_')
					def ProjectName = RevisionArray[0]
					def ProjectVersion = RevisionArray[1]
					def ProjectStatus = RevisionArray[2]
					def ProjectDate = RevisionArray[3]
					
					if(ProjectStatus.contentEquals("release")) ProjectStatus = "Released"
					if(ProjectStatus.contentEquals("integration")) {
						ProjectStatus = "Integration"
					}
					
					//HTML for the table rows in Confluence.
					def rowToAdd = """<tr><th class=\"confluenceTh\"><span style=\"color: rgb(34,34,34);\">"""+ ProjectName +"""</span></th><td class=\"confluenceTd\"><span style=\"color: rgb(34,34,34);\">"""+ProjectStatus+"""</span></td><td class=\"confluenceTd\"><span style=\"color: rgb(34,34,34);\">"""+ProjectVersion+"""</span></td><td class=\"confluenceTd\"><span style=\"color: rgb(34,34,34);\">"""+ProjectDate+"""</span></td></tr>"""
				
					//Only append to the rows if there is more than one.
					if (baseTableRow.equals(null)){
					    baseTableRow = rowToAdd
					} else {
					    baseTableRow = baseTableRow + rowToAdd
					}
				}
			} else {
				baseTableRow = """<tr><th class=\"confluenceTh\"><span style=\"color: rgb(34,34,34);\">No Dependency Found</span></th><td class=\"confluenceTd\"><span style=\"color: rgb(34,34,34);\">---</span></td><td class=\"confluenceTd\"><span style=\"color: rgb(34,34,34);\">---</span></td><td class=\"confluenceTd\"><span style=\"color: rgb(34,34,34);\">---</span></td></tr>"""
			}

			//Include the HTML needed for the rest of the HTML page.
			def tableToAdd = """<h2 id=\"DependencyReport-"""+ Utilities.formatFileName(file) +"""\">"""+ Utilities.formatFileName(file) +"""</h2><div class=\"table-wrap\"><table class=\"confluenceTable\"><colgroup><col/><col/><col/><col/></colgroup><tbody><tr><th class=\"confluenceTh\">Project</th><th class=\"confluenceTh\">Status</th><th class=\"confluenceTh\">Version</th><th class=\"confluenceTh\">Date Released</th></tr>"""+ baseTableRow +"""</tbody></table></div><br></br>"""
			
			//Only append to the rows if there is more than one.
			if (baseHTMLTable.equals(null)){
				baseHTMLTable = tableToAdd
			} else {
				baseHTMLTable = baseHTMLTable + tableToAdd
			}
		}
		baseHTMLTable = baseHTMLTable.replace('\"', '\\"')
		
		//Check if the environment is a release environment.
		def team = System.getenv("team")
		
		def pageTitle
		
		//Mark the Job as enabled or disabled.
		if(currentSuiteRelease){
			pageTitle = 'Report (' + currentSuiteRelease + ') ' + Utilities.getDateTime()
		} else  if(team.contains('Release')){
			pageTitle = 'Report (' + team.substring(0, team.indexOf(" ")) +') ' + Utilities.getDateTime()
		} else {
			pageTitle = 'Report ' + Utilities.getDateTime()
		}
		def parentPageID = getParentPageID(getParentPageJSON())
		baseJSON = '''{"type":"page","ancestors":[{"type":"page","id":'''+ parentPageID[0] +''' }],"title":"Dependency ''' + pageTitle +'''","space":{"key":"DEPEND"},"body":{"storage":{"value":"''' + baseHTMLTable +'''","representation":"storage"}}}'''
		return baseJSON
	}
	
	//Generate the Access Token for the Publisher.
	def generateParentPage(){
		println "Creating the Parent Page ..."
		
		//Ensure the  SuiteRelease is populated. 
		def SuiteRelease
		def team = System.getenv("team")
		
		if (team.contains("Release")){
			SuiteRelease = team.substring(0, team.indexOf(" "))
		} else {
			SuiteRelease = ""
		}
		
		
		def baseHTML = "This Parent Page contains all the dependency reports for " + SuiteRelease
		def ParentJSON = '''{"type":"page","title":"'''+ SuiteRelease +''' Dependency Reports","space":{"key":"DEPEND"},"body":{"storage":{"value":"''' + baseHTML +'''","representation":"storage"}}}'''
		def url = ["curl", "-u", "johnny.jenkins@openjawtech.com:bEGvEREsFcQqwlgQrNkhFF79", "-X", "POST", "-H", "Content-Type:application/json", "-d", ParentJSON, "https://openjawtech.atlassian.net/wiki/rest/api/content/"]
		def stringRS = executeCurlCommand(url)
	}
	
	//Get the Parent Page ID
	def getParentPageJSON(){
		println "Getting the Parent Page ID ..."
		
		//Ensure the  SuiteRelease is populated. 
		def SuiteRelease
		def team = System.getenv("team")
		
		if (team.contains("Release")){
			SuiteRelease = team.substring(0, team.indexOf(" ")) + "+"
		} else {
			SuiteRelease = ""
		}
		
		def parentPageName = SuiteRelease + "Dependency+Reports"	
		def url = ["curl", "-u", "johnny.jenkins@openjawtech.com:bEGvEREsFcQqwlgQrNkhFF79", "-X", "GET","https://openjawtech.atlassian.net/wiki/rest/api/content?title="+ parentPageName]
		def stringRS = executeCurlCommand(url)
		def jsonRS = convertToJSON(stringRS)
		return jsonRS
	}
	
	//Convert Response to JSON
	def convertToJSON(stringRS){
		return new JsonSlurper().parseText(stringRS)
	}
	
	//Get the API ID in the Publisher.
	def getParentPageID(jsonRS){
		def JSONResultID = jsonRS.results.id
		return JSONResultID
	}

	//Generate the Access Token for the Publisher.
	def uploadToConfluence(JSON){
		println "Uploading to Confluence ..."
		def url = ["curl", "-u", "johnny.jenkins@openjawtech.com:bEGvEREsFcQqwlgQrNkhFF79", "-X", "POST", "-H", "Content-Type:application/json", "-d", JSON, "https://openjawtech.atlassian.net/wiki/rest/api/content/"]
		def stringRS = executeCurlCommand(url)
	}
	
	//Execute any CURL Command.
	def executeCurlCommand(URL){
		def proc = URL.execute();
		def outputStream = new StringBuffer();
		proc.waitForProcessOutput(outputStream, System.err)
		return outputStream.toString();
	}
}