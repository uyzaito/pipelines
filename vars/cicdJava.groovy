def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    node ("${pipelineParams.node}") {
        stage('Clonado') {
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
        stage('Construccion') {
            sh "mvn clean install -Dmaven.test.skip=true"
        }
        stage ('Tests') {
            parallel: {
                stage('Test Unitario') {
                    sh "mvn test -f pom.xml"
                }
                stage('Analisis en Sonarqube',) {
                    withSonarQubeEnv {
                        echo " SONAR GOAL --- $SONAR_MAVEN_GOAL"
                        sh "mvn $SONAR_MAVEN_GOAL"
                    }
                }
            }
        }
        stage('Publicar en Nexus'){
            NEXUS_VERSION = "nexus3"
            NEXUS_PROTOCOL = "http"
            NEXUS_URL = "${pipelineParams.nexusurl}"
            NEXUS_REPOSITORY = "${pipelineParams.nexusrepo}"
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
        stage('Construccion s2i & despliegue'){
            openshift.withCluster() {
            openshift.withProject("${pipelineParams.ambiente}") {
                def appSelector = openshift.selector("dc","${IMAGE}")
                def appExists = appSelector.exists()
                if(!appExists) {
                    echo "No existe la imagen ${IMAGE} en el ambiente ${pipelineParams.ambiente}"                 
                    sh """
                        mkdir -p ocp/deployments
                        cp target/${IMAGE}-${VERSION}.${PACKAGE} ocp/deployments/
                        oc project ${pipelineParams.ambiente}
                        oc new-build --binary=true --name=${IMAGE} --image-stream=redhat-openjdk18-openshift
                        oc start-build ${IMAGE} --from-dir=./ocp --follow
                        oc new-app ${IMAGE}
                        oc expose svc/${IMAGE}
                    """
                }else{
                    echo "Si existe la imagen ${IMAGE} en el ambiente ${pipelineParams.ambiente}"
                    sh """
                        mkdir -p ocp/deployments
                        cp target/${IMAGE}-${VERSION}.${PACKAGE} ocp/deployments/
                        oc project ${pipelineParams.ambiente}
                        oc start-build ${IMAGE} --from-dir=./ocp --follow
                    """                    
                }
            }
            
            }            
        }
        stage ('Etiquetado') {
            sh """
                oc tag ${IMAGE}:latest ${IMAGE}:${VERSION}
                oc tag ${IMAGE}:latest grep-staging/${IMAGE}:${VERSION}
                oc tag ${IMAGE}:latest grep-integracion/${IMAGE}:${VERSION}
                oc tag ${IMAGE}:latest grep-prod/${IMAGE}:${VERSION}
            """
        }
        stage ('Disparador') {
            if (pipelineParams.ambiente != "grep-prod") {
                build(
                    job: "${pipelineParams.tareaHija}",
                    wait: wait.toBoolean(),
                    parameters: [
                            [
                                    $class: 'StringParameterValue',
                                    name: 'IMAGE',
                                    value: "${IMAGE}",
                            ],
                            [
                                    $class: 'StringParameterValue',
                                    name: 'VERSION',
                                    value: "${VERSION}",
                            ],
                    ],
                )
            }
        }
    }
}