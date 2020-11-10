import groovy.xml.MarkupBuilder
import groovy.io.FileType
import java.util.regex.Matcher
import java.util.regex.Pattern

class ChangeLogEmail{

    final String COMMIT_REGEX = "(\\b.*Move along, nothing to see here.*\\b)"

    def projectsWithCommits = [];
    def projectsWithNoCommits = [];
    def PlatDevList = [];
    def JIRA

    def addProjectToList(File file){
        if (checkFileForNoCommits(file)){
            projectsWithNoCommits.add(file)
        }
        else{
            projectsWithCommits.add(file)
        }
    }
	
    def checkFileForNoCommits(def file){
        Pattern p = Pattern.compile(COMMIT_REGEX)
        Matcher m = p.matcher(file.text)
        if (m.find()) {
            return m.group()
        }
    }

    def getListOfProjectNames(){
        return parseProjectName()
    }

    def getListOfProjects(){
        return projectsWithCommits.sort() + projectsWithNoCommits.sort()
    }

    def parseProjectName(){
        def projects = getListOfProjects()
        def projectNames = []
        projects.each{ fileName ->
			Pattern p = Pattern.compile("./ChangeLog_(.*?)(_integration|_master|_feature).*")
			Matcher m = p.matcher(fileName.toString())
			if(m.find()) {
				projectNames.add(m.group(1))
			}
        }
        return projectNames.unique()
    }
    
    def parseReleaseProjectName(){
        def projects = getListOfProjects()
        def projectNames = []
        projects.each{ fileName ->
			def fileNameFormatBeginning = './ReleaseNotes_'
			def fileNameFormatEnd = '_master.txt'
			def fileNametoString = fileName.toString()
			int filenameLength = fileNametoString.length()
			int fileNameFormatBeginningLength = fileNameFormatBeginning.toString().length()
			int fileNameFormatEndLength = fileNameFormatEnd.toString().length()
			int productIndex = filenameLength - fileNameFormatEndLength
			def formattedName = fileNametoString.substring(fileNameFormatBeginningLength,productIndex)
			projectNames.add(formattedName)
        }
        return projectNames.unique()
    }
}


