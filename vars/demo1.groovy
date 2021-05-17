def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    node ("${pipelineParams.node}") {
        stage('Clonar') {
          checkout([$class: 'GitSCM',
          branches: [[name: "${pipelineParams.branch}"]],
          extensions: [],
          userRemoteConfigs: [[url: "${pipelineParams.repo}"]]])
        }
        stage('Lectura de Pom') {
                IMAGE = readMavenPom(file: "pom.xml").getArtifactId()
                GROUP = readMavenPom(file: "pom.xml").getGroupId()
                PACKAGE = readMavenPom(file: "pom.xml").getPackaging()
                BUILD = readMavenPom(file: "pom.xml").getBuild()
                NAME = readMavenPom(file: "pom.xml").getName()
                VERSION = readMavenPom(file: "pom.xml").getVersion()
                echo "Image     --- $IMAGE"
                echo "Group     --- $GROUP"
                echo "Package   --- $PACKAGE"
                echo "Build     --- $BUILD"
                echo "Name      --- $NAME"
                echo "Version   --- $VERSION"
        }
        /*stage('Construir') {
            sh "mvn clean install pom.xml -Dmaven.test.skip=true"
        }*/
        stage ('Tests') {
            parallel: {
                stage('Unity test') {
                    sh "mvn test -f pom.xml"
                }
                stage('Sonar Analysis',) {
                    withSonarQubeEnv {
                        echo " SONAR GOAL --- $SONAR_MAVEN_GOAL"
                        sh "mvn $SONAR_MAVEN_GOAL -f pom.xml"
                        //sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar'
                    }
                }
            }
        }
        /*stage('Publicar'){
            sh "mvn deploy:deploy-file -DgeneratePom=false -Dversion=${VERSION} -DgroupId=${GROUP} -DartifactId=${IMAGE} -DrepositoryId=nexus -Durl=${nexusRepo} -Dfile=$branch/target/${IMAGE}-${VERSION}.${PACKAGE} -DuniqueVersion=false"
        }*/
    }
}