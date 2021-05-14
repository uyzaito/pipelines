def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    node ('maven') {
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
                echo "$IMAGE"
                echo "$GROUP"
                echo "$PACKAGE"
                echo "$BUILD"
                echo "$NAME"
                echo "$VERSION"
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
                        sh "mvn $SONAR_MAVEN_GOAL -f pom.xml"
                    }
                }
            }
        }
        /*stage('Publicar'){
            sh "mvn deploy:deploy-file -DgeneratePom=false -Dversion=${VERSION} -DgroupId=${GROUP} -DartifactId=${IMAGE} -DrepositoryId=nexus -Durl=${nexusRepo} -Dfile=$branch/target/${IMAGE}-${VERSION}.${PACKAGE} -DuniqueVersion=false"
        }*/
    }
}