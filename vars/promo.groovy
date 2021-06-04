def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    if (IMAGE == null){
        def VERSION = "${params.VERSION}"
        def IMAGE = "${pipelineParams.IMAGE}"
    }

    echo "ESTAMOS DESPLEGANDO LA IMAGEN $IMAGE Y LA VERSION $VERSION EN EL AMBIENTE $pipelineParams.ambiente"

    node ("${pipelineParams.node}") {
        stage ('Aceptacion de promocion') {
            try {
                timeout(time:7, unit:'DAYS') {
                    def userInput = input(
                        id: 'userInput',
                        message: "¿ APRUEBA LA EL DESPLIEGUE DE ${IMAGE} VERSION ${VERSION} EN EL AMBIENTE ${pipelineParams.ambiente} ?",
                        parameters: [[$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: 'acepta']]

                        echo " userInput.acepta $userInput.acepta"
                    )
                }    
            } catch (error) {
                echo "Error con input de aceptación----- error" 
            }        
        }
        if ( userInput.acepta == true ) { 
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
}
