def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    //if ("$env.IMAGE" == null){
        echo "SETEOS DE VARIABLES MANUAL VERSION = $VERSION"
        def VERSION = "${params.VERSION}"
        def IMAGE = "${pipelineParams.IMAGE}"
    //}

    echo "ESTAMOS DESPLEGANDO LA IMAGEN $pipelineParams.IMAGE Y LA VERSION $params.VERSION EN EL AMBIENTE $pipelineParams.ambiente"

    node ("${pipelineParams.node}") {
        stage ('Aceptacion de promocion') {
            try {
                timeout(time:7, unit:'DAYS') {
                    userInput = input(
                        id: 'userInput',
                        message: "¿ APRUEBA LA EL DESPLIEGUE DE ${IMAGE} VERSION ${VERSION} EN EL AMBIENTE ${pipelineParams.ambiente} ?",
                    )
                }    
            } catch (error) {
                echo "Error con input de aceptación----- error" 
            }        
        }
        stage('Promocion'){
            openshift.withCluster() {
                openshift.withProject("${pipelineParams.ambiente}") {
                    def imageStreamSelector = openshift.selector("dc","${IMAGE}")
                    def imageStreamExists = imageStreamSelector.exists()
                    if(!imageStreamExists) {
                        echo "No existe la imagen ${IMAGE} en el ambiente ${pipelineParams.ambiente}"                 
                        sh """
                            oc project ${pipelineParams.ambiente}
                            oc new-app ${IMAGE}:${VERSION}
                            oc expose svc/${IMAGE}:${VERSION}
                        """
                    }else{
                        echo "Si existe la imagen ${IMAGE} en el ambiente ${pipelineParams.ambiente}"
                        openshiftDeploy(depCfg: "${IMAGE}:${VERSION}", namespace: "${pipelineParams.ambiente}", waitTime: '10', waitUnit: 'min')
                    }
                }                
            }            
        }
        stage ('Disparador') {            
            build(
                job: "${pipelineParams.tareaHija}",
                wait: false,
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
