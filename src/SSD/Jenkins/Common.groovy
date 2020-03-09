package org.common   //Mentioned package with this file name 

import hudson.model.*  // imported for default jenkins class 
import hudson.FilePath // for file operation 
import groovy.io.FileType // to get file of interest
import groovy.transform.Field  //to make use of @field for variable scope


def check(repo,branchName)  //Method to clone repo , need repo url and branchName as a argument. 
	{
 	 	checkout changelog: true, poll: true, scm: [$class: 'GitSCM', branches: [[name: "$branchName"]], doGenerateSubmoduleConfigurations: false,
    extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]],
    submoduleCfg: [], userRemoteConfigs: [[credentialsId: '477cf2d2-45a6-4447-ad1b-26c97b696278', url: "$repo"]]]	
	}

def readBaseVersion()   // I assumend to calculate version from code commit , so defind base version in version.txt file.
	{
    String version = readFile("version.txt")
		//println "'" + version + "
		String[] versions = version.split("\\.")
		def major_version = versions[0]
		def minor_version = versions[1]
		def Patch = versions[2]
    return [major_version, minor_version, Patch ]
	}
def getCommitNumber()  // getting commit number
	{
    branchName = BRANCH_NAME
    def command = "git rev-list --count origin/$branchName"
		String[] gitCommitCountBat = bat(returnStdout: true, script: command).split("\r?\n")
		def commitNumber = gitCommitCountBat[gitCommitCountBat.size()-1]
		return commitNumber
  }
def getGitSha()  // getting sha-1 code for perticular commt , intial 8 character out of 40 
	{
    String[] gitCommitBat = bat(returnStdout: true, script: 'git rev-parse HEAD').split("\r?\n")
	  def git_commit = gitCommitBat[gitCommitBat.size()-1]
	  def git_sha = git_commit.substring(0,8)
    return git_sha
  }
def getGitAllversions()  // generating revision number , revision version. 
	{
   	(major_version, minor_version, Patch) = readBaseVersion()
   	commitNumber = getCommitNumber()
   	git_sha = getGitSha()
   	println "GIT SHA is: $git_sha"
   	def revision_number = (Integer.parseInt(commitNumber)) % 10000
		def revision_version = String.format('%04d',revision_number)
	 	println "revision_version " + revision_version 
		minor_version = Integer.parseInt(minor_version) + ((Integer.parseInt(commitNumber)) / 10000) as Integer
		return [major_version, minor_version, Patch, revision_number, revision_version, git_sha]
  }
def getVersion(branchName)  // generating versions for prodversion , file version and nuget 
	{
    (major_version, minor_version, Patch, revision_number, revision_version, git_sha) = getGitAllversions()
   	def prefix=branchName.substring(0,1)
		prefix2="."
		prefix3=""
		if (prefix=="r") prefix2="-rc-"
		if (prefix=="d") prefix2="-beta-"
		if (prefix=="r") prefix3="r"
		if (prefix=="d") prefix3="d"
		full_version="$major_version.$minor_version.$Patch.$revision_version"
		prod_version="$major_version.$minor_version.$Patch$prefix2$revision_version$prefix3"
		full_version_sha="$major_version.$minor_version.$Patch.$revision_version [$git_sha]"
   	nuget_vrsion = "$major_version.$minor_version.$Patch$prefix2$revision_version"
  	println "$full_version|$full_version_sha|$prod_version"
		writeFile file: "FullVersion.txt", text: full_version
		writeFile file: "change_log.txt", text: currentBuild.changeSets.dump()
		writeFile file: "source.txt", text: currentBuild.absoluteUrl
  	return [full_version, prod_version, full_version_sha, nuget_vrsion]
   }

@NonCPS
def get_unitTestFiles()   // To get tests files , which are ending with .Tests and also under bin/release folder.
	{
   	unitTestFiles = []
   	dh = new File("${WORKSPACE}")
   	dh.eachFileRecurse (FileType.FILES)
   		{
   			if((it.path.matches("(.*)Release(.*)")) && (it.path.matches("(.*)bin(.*)")) && ( it.name.endsWith('.Tests.dll')))
         	{
       			unitTestFiles << it
    			}
   		}
   	return unitTestFiles
  }

@NonCPS
def getChangeString()  //Getting last commit msg to display in email notification 
	{
		MAX_MSG_LEN = 100
  	def changeString = ""
  	def changeLogSets = currentBuild.rawBuild.changeSets
  	for (int i = 0; i < changeLogSets.size(); i++)
  		{
   			def entries = changeLogSets[i].items
   			for (int j = 0; j < entries.length; j++)
   				{
  					def entry = entries[j]
   					truncated_msg = entry.msg.take(MAX_MSG_LEN)
   					changeString = " - [${entry.author}] ${truncated_msg} \n"
   					return changeString
   				}
  		}
  	if (!changeString)
  		{
   			changeString = " - No new changes"
   			return changeString
  		}
	}

def emailnotify(branchName,unstableCounter,errorCounter,change,mailList,unstableCauses) // notification method
	{
   	admin = 'khawaleshrikant@hotmail.com'
   	if (branchName=="master" ||  branchName=="develop" || (branchName).startsWith('support'))
 	    {
        mailRecipients = mailList
 	    }
   	else 
 	    {
        mailRecipients = admin
 	    }
   	if (unstableCounter > 0 && errorCounter == 0)
     	{
     		emailext attachLog: true,
       	body: """ <p><strong>Build #  $BUILD_NUMBER&nbsp; is </strong> - <strong><span style="color: rgb(247, 218, 100);">Unstable</span></strong></p>
			  <p><strong>Output : $BUILD_URL&nbsp;</strong></p>
			  <p><strong>Error Occurred :</strong><strong><span style="color: rgb(84, 172, 210);">&nbsp;</span><span style="color: rgb(235, 107, 86);">$unstableCauses</span></strong></p>
   		  <p><strong>ChangeLog </strong>:<br>&emsp;$change </p>""",
       	recipientProviders: [culprits()], 
       	replyTo: admin, 
       	subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Unstable',
        to:"${mailRecipients}" 
   
     		currentBuild.result = "UNSTABLE"
  		}
   	else if (errorCounter > 0)
     	{
     		emailext attachLog: true,
     		body: """ <p><strong>Build #  $BUILD_NUMBER&nbsp; is </strong> - <strong><span style="color: rgb(235, 107, 86);">Failed</span></strong></p>
	 		  <p><strong>Output : $BUILD_URL&nbsp;</strong></p>
	 		  <p><strong>Error Occurred :</strong><strong><span style="color: rgb(84, 172, 210);">&nbsp;</span><span style="color: rgb(235, 107, 86);">$unstableCauses</span></strong></p>
        <p><strong>ChangeLog </strong>:<br>&emsp;$change </p>""",
     		recipientProviders: [culprits()], 
     		replyTo: admin, 
     		subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Failed',
     		to:"${mailRecipients}"
          	 
     		println "The build failed due to " + errorCounter + " errors  and the reason is " + unstableCauses 
     		currentBuild.result = "FAILED"
     		println "Done."
     		throw new Exception();
			}
   	else 
     	{
    		emailext attachLog: true,
     		body: """ <p><strong>Build #  $BUILD_NUMBER&nbsp; is </strong> - <strong><span style="color: rgb(97, 189, 109);">Successful</span></strong></p>
	 		  <p><strong>Output : $BUILD_URL&nbsp;</strong></p>
	 		  <p><strong>Error Occurred :</strong><strong><span style="color: rgb(84, 172, 210);">&nbsp;</span><span style="color: rgb(97, 189, 109);">No Errors </span></strong></p>
   		  <p><strong>ChangeLog </strong>:<br>&emsp;$change </p>""",
   	    recipientProviders: [culprits()], 
     		replyTo: admin, 
     		subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - Successful',
     		to:"${mailRecipients}"
			}
  }

def runDoxygen(doxconfig)  //To run doxygen step if we have doxygen config file. 
 {
  def doxygen = tool name: 'Doxygen', type: 'hudson.plugins.doxygen.DoxygenInstallation'
  bat """ "${doxygen}" ${doxconfig} """
 }

def runHtmlPublish(reportDir, reportFiles, reportName, reportTitles)  // To publish HTML report 
 {
  	publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: reportDir, reportFiles: reportFiles, reportName: reportName, reportTitles: reportTitles] )
 }

def runXUnitTest()    //Running xUnit test 
 {
    unstableCauses = ""
    unstableCounter = 0
    unitTestFiles = []
	  unitTestFiles = get_unitTestFiles()
	  def xUnitTest = "$JENKINS_HOME\\TOOLS\\xUnit\\tools\\net472\\xunit.console.x86.exe"
	  println xUnitTest
	  String files = ""
	  for (int m = 0; m <unitTestFiles.size(); m++) {
			  files = files + " \"" +unitTestFiles[m] + "\""
		}
	
	  def code = bat returnStatus: true, script: "\"$xUnitTest\" $files -xml test_results.xml"
		if (code > 0){
			  String reason = "Unit tests failed. Error code: " + code
			  println reason
			  unstableCauses += reason + "\r\n"
			  unstableCounter++
			  unitTestResult = "Failed"
		}
	  else{
			unitTestResult = "Passed"
		}
	  return [unstableCauses, unstableCounter, unitTestResult]
}

def runCodeCoverageXunit()  //To run Code coverage scan to generate code coverage report 
  {
    String testAssemblyFiles = ""
    for (int m = 0; m <unitTestFiles.size(); m++) {
		testAssemblyFiles = testAssemblyFiles + " \"" +unitTestFiles[m] + "\""
	}
          
  def xUnitTest = "$JENKINS_HOME\\TOOLS\\xUnit\\tools\\net472\\xunit.console.x86.exe"
  def openCover = tool name: 'OpenCover', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
  def reportgen = tool name: 'ReportGenerator', type: 'com.cloudbees.jenkins.plugins.customtools.CustomTool'
      		
  bat """
      	"${openCover}\\OpenCover.Console.exe"^
   	    -register:admin^
         "-target:${xUnitTest}"^
   	     "-targetargs:${testAssemblyFiles} -"noshadow""^
   	    -mergebyhash^
   	    -filter:"+[Core*]* -[*Tests]* -[xunit*]*"	
         "${reportgen}\\ReportGenerator.exe"^
        -reports:"%WORKSPACE%\\results.xml"^
        -targetdir:"%WORKSPACE%\\UnitTests\\report"^
        -sourcedirs:"%WORKSPACE%\\UnitTests"
	"""
}

def runNugetPack(projFile,Configuration,Platform)  // To pack Nuget 
  {
    bat """ if not exist "created_packages" mkdir created_packages"""
    def output_path = "created_packages"
    bat """nuget pack ${projFile} -Verbosity detailed -IncludeReferencedProjects -Prop Configuration=${Configuration} -Prop Platform=${Platform} -OutputDirectory ${output_path} -version ${nuget_vrsion}"""
  }

def runNugetPush()    //To push Nuget 
  {
	  bat """nuget push created_packages\\* -Source https://artifactory-host/artifactory/api/nuget/StandardComponents"""
  }

def createJIRATicket(JIRA_SITE, key, Components)  // If build is failing JIRA ticket should get created. 
  {
    withEnv(["JIRA_SITE=$JIRA_SITE"]){
      def testIssue = [fields: [ // id or key must present for project.
      project: [key: $key],
      summary: 'Build Failed (Ticket by Jenkins)',
      description: "Develop/Release/Master Build is failed need to check here \n $BUILD_URL", 
      // id or name must present for issueType.
      issuetype: [name: 'Task'],
      priority: [name: 'Critical'],
      components: [[name:$Components]]]]
			response = jiraNewIssue issue: testIssue
			echo response.successful.toString()
			echo response.data.toString()
  		}
  }
return this
