class Commit{
    String commitHash
    String commitAuthor
    String commitDate
    String description
    String merge

    def buildCommitUrl(def url, def destBranch, project){
        def fork = destBranch.split("/")[0]
        return "${url}$fork/$project/commit/${this.commitHash}"
    }
}
