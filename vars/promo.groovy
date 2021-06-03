def call(body) {
	def pipelineParams = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = pipelineParams
	body()

    node ("${pipelineParams.node}") { 
        stage('Promocion'){
            // iteracion por ambientes con aprobacion 
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
        stage ('Aceptacion de promocion') {
            if (pipelineParams.ambiente != "grep-prod") {
                try {
                    timeout(time:7, unit:'DAYS') {
                        userInput = input(
                            id: 'userInput',
                            message: "¿ APRUEBA LA EL DESPLIEGUE DE ${IMAGE}:${VERSION} EN EL SIGUENTE AMBIENTE ? , ATENCION !!!! TENGA EN CUENTA QUE UD ESTA EVALUANDO AHORA DESDE ${pipelineParams.ambiente}",
                            parameters: [
                            [$class: 'BooleanParameterDefinition', defaultValue: false, description: '', name: 'acepta']
                        )
                    }    
                } catch (error) {
                    echo "Error con input de aceptación----- error" 
                }
            }            
        }
        stage ('Disparador') {
            if ( userInput.acepta == true ) {
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
