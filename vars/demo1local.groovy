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
                    withSonarQubeEnv {
                        echo " SONAR GOAL --- $SONAR_MAVEN_GOAL"
                        sh "mvn jacoco:report $SONAR_MAVEN_GOAL -f pom.xml"
                        //sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.7.0.1746:sonar'
                    }
                }
            }
        }
        stage('Publicar'){
            //sh "mvn deploy:deploy-file -DgeneratePom=false -Dversion=${VERSION} -DgroupId=${GROUP} -DartifactId=${IMAGE} -DrepositoryId=nexus -Durl=${pipelineParams.nexusrepo} -Dfile=target/${IMAGE}-${VERSION}.${PACKAGE} -DuniqueVersion=false"
            // Read POM xml file using 'readMavenPom' step , this step 'readMavenPom' is included in: https://plugins.jenkins.io/pipeline-utility-steps
            // This can be nexus3 or nexus2
            NEXUS_VERSION = "nexus3"
            // This can be http or https
            NEXUS_PROTOCOL = "http"
            // Where your Nexus is running
            NEXUS_URL = "nexus-cicd.router.default.svc.cluster.local"
            // Repository where we will upload the artifact
            NEXUS_REPOSITORY = "maven-releases"
            // Jenkins credential id to authenticate to Nexus OSS
            NEXUS_CREDENTIAL_ID = "nexus-credentials"
                    pom = readMavenPom file: "pom.xml";
                    // Find built artifact under target folder
                    filesByGlob = findFiles(glob: "target/*.${pom.packaging}");
                    // Print some info from the artifact found
                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                    // Extract the path from the File found
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
        /*node ('master'){         
            stage('Build Image'){                 
                sh """
                    mkdir -p ocp/deployments
                    cp target/${IMAGE}-${VERSION}.${PACKAGE} ocp/deployments/
                    oc new-build --binary=true --name=${IMAGE} --image-stream=redhat-openjdk18-openshift
                    oc start-build jdk-us-app --from-dir=./ocp --follow
                    oc new-app jdk-us-app
                """
            }

        }*/
        stage('s2i'){
            openshift.withCluster(){
                openshift.withProject("${pipelineParams.ambiente}"){
                    def build = openshift.selector("bc", IMAGE);
                    def startedBuild = build.startBuild("--from-file=target/${IMAGE}-${VERSION}.${PACKAGE}");
                    startedBuild.logs('-f');
                    echo "${IMAGE} build status: ${startedBuild.object().status}";
                }                
            }
        }
    }
}