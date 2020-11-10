import groovy.xml.MarkupBuilder
import groovy.io.FileType
import ChangeLogEmail
import java.util.regex.Matcher
import java.util.regex.Pattern

def emailTitle = System.getenv()['JOB_BASE_NAME'].replace("_", " ")

ChangeLogEmail master = new ChangeLogEmail();
ChangeLogEmail integration = new ChangeLogEmail();
ChangeLogEmail feature = new ChangeLogEmail();
ChangeLogEmail noIntegrationBranchFeatures = new ChangeLogEmail();

def dir = new File(".")
dir.eachFile (FileType.FILES) { file ->
    if(file.getPath().endsWith(".txt")){
        if(file.getPath().contains("master")){
            master.addProjectToList(file)
        }
        else if(file.getPath().contains("integration")){
            integration.addProjectToList(file)
        }
        else if(file.getPath().contains("feature")){
            feature.addProjectToList(file)
        }
		else if(file.getPath().contains("NoIntegrationBranch")){
			noIntegrationBranchFeatures.addProjectToList(file)
		}
    }
}
println "master: " + master.getListOfProjects()
println "Integration: " + integration.getListOfProjects()
println "feature: " + feature.getListOfProjects()
println "nobranch: " + noIntegrationBranchFeatures.getListOfProjects()

def masterListOfProjects = master.getListOfProjects() + noIntegrationBranchFeatures.getListOfProjects()
def masterListOfProjectNames = master.getListOfProjectNames() + noIntegrationBranchFeatures.getListOfProjectNames()
createHtmlPage(masterListOfProjects, masterListOfProjectNames, "master.html", emailTitle)
createHtmlPage(integration.getListOfProjects(),  integration.getListOfProjectNames(), "sprint.html", emailTitle)
createHtmlPage(feature.getListOfProjects(),  feature.getListOfProjectNames(), "features.html", emailTitle)


def createHtmlPage(def projectFiles, def projectNames, def outputFileName, def emailTitle){
    def writer = new StringWriter()
    def htmlBuilder = new MarkupBuilder(writer)

    htmlBuilder.html('lang' : 'en-ie') {
        head(){
            meta('http-equiv' : 'Content-Type', 'content' : 'text/html; charset=UTF-8')
        }
        body() {
            div(style: """-webkit-border-radius: 6px; -moz-border-radius: 6px; border-radius: 6px; font-size: 14px; padding: 6px; border: 1px solid #ccc; width: 1080px;
            background-image: linear-gradient(#250576, #24065b, #24054f, #25043c); margin: 0 auto;"""){
            	div(style:"height:260px"){
            	    img(src:"http://dublin.openjawtech.com/images/test_report/tRetailIcon.gif", align:"right")
					def Team = System.getenv()['team']
            	    span(style:"font-size: 40px;color:white;font-family: Arial, Helvetica, sans-serif;font-weight: lighter;line-height:200px;padding-left:40px", Team + " ChangeLog Comparison")
            	}

                div(style:"""border-radius: 10px; width: 100%; margin: 5px 0 5px 0%; transition: height 0.5s;
                            -webkit-transition: height 0.5s; text-align: left; overflow: hidden; background-color: #1c96fc;margin-bottom: 15px;padding-bottom: 10px;"""){
                    h2(style:"font-size: 1.6rem; font-weight: 400; color: white; text-align: center;"){
                        span("${emailTitle}")
                    }
                    table(style:"padding-left: 16px"){
                        tbody{
                            tr{
                                th(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: white;","Build:")
                                td{
                                    a(href: "${System.getenv("BUILD_URL")}") {
                                        span(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: #551aa8;","${System.getenv("BUILD_DISPLAY_NAME")}")
                                    }
                                }
                            }
                            tr{
                                th(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: white;","Date:")
                                td(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: white;",new Date().toString())
                            }
                            tr{
                                th(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: white;","Changes:")
                                td{
                                    span(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: white;","Full Change Log can be found ")
                                    a(href: "${System.getenv("BUILD_URL")}") {
                                        span(style:"font-style: normal;font-weight: 400;font-size: 1.2rem;color: #551aa8;","here")
                                    }
                                }
                            }
                        }
                    }
                }
				div(style:"""border-radius: 10px; width: 100%; margin: 5px 0 5px 0%; transition: height 0.5s;
                            -webkit-transition: height 0.5s; text-align: left; overflow: hidden; background-color: #1c96fc"""){
                    h2(style:"font-size: 1.6rem; font-weight: 400; color: white; text-align: center;","Projects")
                        projectNames.each{ project ->
							div(style: "width: 210px; float: left;background-color: #25043c;border-radius: 10px; text-align: center; line-height:25px;;margin-left:3px;margin-bottom:3px"){
                                a(style:"text-decoration: none;",href: "#${project}"){
                                    span(style:"font-style: normal;font-weight: 400;font-size: 0.9rem;color: white;text-decoration: none;","${project}")
                                }
                            }
                        }
                }
                projectFiles.each{
                    def htmlLine = it.readLines()
                    htmlLine.each{
                        mkp.yieldUnescaped it.replaceAll("<!DOCTYPE html>", "").replaceAll("<call>", "").replaceAll("</call>", "")
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


