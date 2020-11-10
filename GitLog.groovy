import Commit

class GitLog{
	List<Commit> commits;
	def sourceBranch
	def destBranch
	def project
	def patch

	GitLog(def destBranch, def sourceBranch, def project, def patch){
		this.destBranch = destBranch
		this.sourceBranch = sourceBranch
		this.project = project
		this.commits = new ArrayList<>();
		this.patch = patch
		getListofCommits();
	}

	def getCommits(){
		return this.commits;
	}

	def getListofCommits(){
		def log = getChangeLog().readLines()
		for(int currentLine = 0; currentLine < log.size(); currentLine++){
			if(parseLine(log[currentLine])){
				Commit commit = new Commit()
				commit.commitHash = log[currentLine].split(" ")[1]
				currentLine++
				if(log[currentLine].substring(0, 5).equals("Merge")){
					commit.merge = log[currentLine]
					currentLine++
				}
				commit.commitAuthor = log[currentLine]
				currentLine++
				commit.commitDate = log[currentLine]
				String description = "";
				while(!parseLine(log[currentLine+1]) && currentLine < log.size()-1){
					description += log[currentLine+1] + "\n"
					currentLine++
				}
				commit.description = description
				commits.add(commit)
			}
		}
	}

	//Returns a change log, based on the differences between two commits.
	//If the change log is comparing a branch to a tag. Compare the commit IDs
	def getChangeLog(){
		def team_repo = "Orchid"
		def source = this.sourceBranch
		def dest = this.destBranch
		try{
			//Doing a Patch Release, compare the Latest Release Tag to the release branch.
			if (this.patch.equals("true")) {
				
				//Append the repo if its not already apart of the variable.
				if (!source.contains("/")) source = team_repo + "/" + source
				
				def sourceCommit = executeCommand("git rev-parse ${source}")
				def destCommit = executeCommand("git rev-list -n 1 ${dest}")
				
				if (sourceCommit && destCommit) {
					def command = "git log ${destCommit}..${sourceCommit}".replaceAll("\n", "")
					def comparison = executeCommand(command)
					return comparison
				}
			} else {
				//Append the repo if its not already apart of the variable. 
				if (!source.contains("/")) source = team_repo + "/" + source
				if (!dest.contains("/")) dest = team_repo + "/" + dest
				
				println "git log ${dest}..${source}"
				def command = executeCommand("git log ${dest}..${source}")
				return command
			}
		}catch(Exception e){
			println e.getMessage()
			System.exit(1)
		}
	}

	def parseLine(line){
		try{
			if(line.substring(0, 6).equals("commit")){
				return true;
			}
			else{
				return false
			}
		}catch(StringIndexOutOfBoundsException e){
			return false;
		}catch(NullPointerException e){
			return false;
		}
	}

	def getLogHeader(){
		return "On $sourceBranch but not on $destBranch"
	}
	
	def executeCommand(def commandString){
		println "Command to execute: " + commandString
		int count = 0;
		def sout = new StringBuffer()
		def serr = new StringBuffer()
		def command = commandString.execute()
		command.waitForProcessOutput(sout,serr)
		while(sout.toString() == "" && serr.toString() == "" && count<10) {
			sleep 500
			count++
			command = commandString.execute()
			command.waitForProcessOutput(sout,serr)
			if(count.equals(9) && sout.toString() == "") println "No Comparisons Detected."
		}
		
		if(serr.toString().length() != 0){
			println "Err: $serr"
			throw new Exception(serr.toString());
		}
		
		return sout
	}
}
