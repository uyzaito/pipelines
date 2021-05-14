def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    node ('maven') {
        stage('Limpiar') {
            deleteDir()
        }
        stage('Clonar') {
            checkout(scm: [$class: 'GitSCM', userRemoteConfigs: [[url: "${repo}", name: "${branch}"], [credentialsId: 'cicd']]])
        }
        stage('Lectura de Pom') {
                IMAGE = readMavenPom(file: "$branch/pom.xml").getArtifactId()
                GROUP = readMavenPom(file: "$branch/pom.xml").getGroupId()
                PACKAGE = readMavenPom(file: "$branch/pom.xml").getPackaging()
                BUILD = readMavenPom(file: "$branch/pom.xml").getBuild()
                NAME = readMavenPom(file: "$branch/pom.xml").getName()
                VERSION = readMavenPom(file: "$branch/pom.xml").getVersion()
                echo "$IMAGE"
                echo "$GROUP"
                echo "$PACKAGE"
                echo "$BUILD"
                echo "$NAME"
                echo "$VERSION"
        }        
        stage('Construir') {
            sh "mvn clean install $branch/pom.xml -Dmaven.test.skip=true"
        }
        stage ('Tests') {
            parallel: {
                stage('Unity test') {
                    sh "mvn test -f $branch/pom.xml"
                }
                stage('Sonar Analysis',) {
                    withSonarQubeEnv('sonar') {
                        sh "mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.2:sonar -f $branch/pom.xml"
                    }
                }
            }
        }
        stage('Publicar'){
            sh "mvn deploy:deploy-file -DgeneratePom=false -Dversion=${VERSION} -DgroupId=${GROUP} -DartifactId=${IMAGE} -DrepositoryId=nexus -Durl=${nexusRepo} -Dfile=$branch/target/${IMAGE}-${VERSION}.${PACKAGE} -DuniqueVersion=false"
        }
    }
}