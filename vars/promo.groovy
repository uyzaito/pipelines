def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    if (!binding.variables.containsKey("IMAGE")){
        def VERSION = "${params.VERSION}"
        def IMAGE = "${pipelineParams.IMAGE}"
    }

    echo sh(script: 'env|sort', returnStdout: true)

    //echo "ESTAMOS DESPLEGANDO LA IMAGEN $IMAGE Y LA VERSION $VERSION EN EL AMBIENTE $pipelineParams.ambiente"

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
                            oc new-app ${IMAGE}
                            oc expose svc/${IMAGE}
                        """
                        openshiftDeploy(depCfg: "${IMAGE}:${VERSION}", namespace: "${pipelineParams.ambiente}", waitTime: '10', waitUnit: 'min')
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
