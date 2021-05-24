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
        stage('Construir') {
            sh "mvn clean install -Dmaven.test.skip=true"
        }
        stage ('Tests') {
            parallel: {
                stage('Test Unitario') {
                    sh "mvn test -f pom.xml"
                }
                stage('Analisis Sonarqube',) {
                    def mvnHome = tool name: 'maven 3.6.0', type: 'maven'
                    withSonarQubeEnv ('sonar4.2') {
                        echo " SONAR GOAL --- $SONAR_MAVEN_GOAL"
                        sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:4.2.2.2472:sonar'
                    }
                }
            }
        }
        stage('Publicar'){
            NEXUS_VERSION = "nexus3"
            NEXUS_PROTOCOL = "https"
            NEXUS_URL = "${pipelineParams.nexusurl}"
            NEXUS_REPOSITORY = "maven-releases"
            NEXUS_CREDENTIAL_ID = "nexus-credentials"
                    pom = readMavenPom file: "pom.xml";
                    filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                    artifactPath = filesByGlob[0].path;
                    // Assign to a boolean response verifying If the artifact name exists
                    artifactExists = fileExists artifactPath;
                    if(artifactExists) {
                        echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}";
                        nexusArtifactUploader(
                            nexusVersion: NEXUS_VERSION,
                            protocol: NEXUS_PROTOCOL,
                            nexusUrl: NEXUS_URL,
                            groupId: pom.groupId,
                            version: pom.version,
                            repository: NEXUS_REPOSITORY,
                            credentialsId: NEXUS_CREDENTIAL_ID,
                            artifacts: [
                                // Artifact generated such as .jar, .ear and .war files.
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: artifactPath,
                                type: pom.packaging],
                                // Lets upload the pom.xml file for additional information for Transitive dependencies
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: "pom.xml",
                                type: "pom"]
                            ]
                        );

                    } else {
                        error "*** File: ${artifactPath}, could not be found";
                    }
        }   
        stage('s2i'){                 
            sh """
                mkdir -p ocp/deployments
                cp target/${IMAGE}-${VERSION}.${PACKAGE} ocp/deployments/
                oc project ${pipelineParams.ambiente}
                oc new-build --binary=true --name=${IMAGE} --image-stream=redhat-openjdk18-openshift
                oc start-build ${IMAGE} --from-dir=./ocp --follow
                oc new-app ${IMAGE}
                oc expose svc/${IMAGE}
            """
        }
    }
}
