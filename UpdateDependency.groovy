import java.util.regex.Pattern
import java.util.regex.Matcher

def projectType = args[0]
def dependencyVersion = args[1]

if (projectType.toUpperCase().equals("GRADLE")) {
    def newLines = []

    new File ("./build.gradle").eachLine { line ->
        if (line.contains("certifiedschema")){
            Pattern p = Pattern.compile("(.*com.openjaw.*openjaw-certifiedschemas.*)(['|\"].*['|\"])(.*)");
            Matcher m = p.matcher(line);
            if(m.find()){
                StringBuffer s = new StringBuffer();
                println "Line before version replacement" + m.group(0)
                m.appendReplacement(s, "\$1'$dependencyVersion'\$3");
                println "Line after version replacement" + s.toString()
                line = s.toString()
            }
        }
        newLines << line
    }

    new File ("./build.gradle").withWriter{ out ->
        newLines.each {out.println it}
    }
}
else if (projectType.toUpperCase().equals("ANT")){
    File file = new File("./ivy.xml")
    println "Parsing dependencies from ${file}"

    XmlParser xmlPlurper = new XmlParser()
    def doc = xmlPlurper.parse(file)

    doc.dependencies.dependency.each { dependency ->
        if (dependency.@name.toString().toUpperCase().equals("CERTIFIEDSCHEMAS")){
            dependency.@rev = "latest.integration"
            dependency.@branch = dependencyVersion
        }
    }

    PrintWriter printWriter = new PrintWriter(file)
    printWriter.write(groovy.xml.XmlUtil.serialize( doc ).replaceAll('&gt;', '>'))
    printWriter.close()
}
