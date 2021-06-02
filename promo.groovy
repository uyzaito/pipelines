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
                }else{
                    echo "Si existe la imagen ${IMAGE} en el ambiente ${pipelineParams.ambiente}"
                    sh """
                        mkdir -p ocp/deployments
                        cp target/${IMAGE}-${VERSION}.${PACKAGE} ocp/deployments/
                        oc project ${pipelineParams.ambiente}
                        oc start-build ${IMAGE} --from-dir=./ocp --follow
                    """
                    openshiftDeploy(depCfg: "${IMAGE}", namespace: "${pipelineParams.ambiente}", waitTime: '10', waitUnit: 'min')
                }
            }
            
            }            
        }
    }
}
