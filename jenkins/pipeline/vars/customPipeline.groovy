// Jenkins pipeline example
// https://www.jenkins.io/doc/book/pipeline/shared-libraries/

// Required credentials:
// - gitCredentialsId: for SCM checkout
// - registryCredentialsId: for docker image push
// - remoteCredentialsId: for SSH command

// Required plugins:
// - SSH Pipeline Steps: Remote publish

/**
 * Default Parameters:
 *  [currentEnv: 'test',
 *   stackName: 'app',
 *
 *   gitUrl: null,
 *   gitCredentialsId: 'git-for-jenkins',
 *   defaultGitLabel: 'dev',
 *   defaultSubLabels: '',
 *
 *   registryUrl: env.REGISTRY_URL,
 *   registryCredentialsId: 'docker-for-jenkins',
 *
 *   remoteHost: env.REMOTE_HOST,
 *   remotePort: env.REMOTE_PORT,
 *   remoteCredentialsId: 'ssh-for-jenkins',
 *
 *   organization: 'demo',
 *   modules: [.],
 *   imageMapping: [],
 *
 *   nodeRunCmd: 'build',
 *   cleanWorkspace: true
 *  ]
 * 
 * Default Environment Variables:
 *   MAVEN_CENTRAL_MIRROR: 'http://maven.aliyun.com/nexus/content/repositories/central/'
 *   MAVEN_IMAGE: 'maven:3.8-jdk-8-slim'
 *   NODE_MIRROR: 'https://registry.npmmirror.com'
 *   NODE_IMAGE: 'node:14.17-slim'
 */
def call(Map config) {
    pipeline {
        agent any

        options { timestamps() }

        parameters {
            // comment GIT_LABEL for multi-branch pipeline
            string defaultValue: (config.defaultGitLabel ?: 'dev'), description: 'Git Branch/Tag', name: 'GIT_LABEL', trim: true
            // comment MODULE_DIR for top project
            choice choices: (config.modules ?: ['.']), description: 'Module Path', name: 'MODULE_DIR'
            // comment MODULE_LABELS for one repository
            string defaultValue: (config.defaultSubLabels), description: 'Git Submodule Branch, format：module1:label1,module2:label2', name: 'MODULE_LABELS', trim: true

            string defaultValue: 'latest', description: 'Image tag', name: 'IMAGE_TAG', trim: true
            choice choices: ['N/A', 'Build & Deploy', 'Build Only', 'Deploy Only'], description: 'Action', name: 'ACTION'
        }

        environment {
            // prod or test
            CURRENT_ENV = "${config.currentEnv ?: 'test'}"
            STACK_NAME = "${config.stackName ?: 'app'}"

            // git config
            GIT_URL = "${config.gitUrl}"
            GIT_CREDENTIALS_ID = "${config.gitCredentialsId ?: 'git-for-jenkins'}"

            // docker registry config
            REGISTRY_URL = config.getOrDefault('registryUrl', env.REGISTRY_URL)
            REGISTRY_CREDENTIALS_ID = "${config.registryCredentialsId ?: 'docker-for-jenkins'}"

            // remote config
            REMOTE_HOST = config.getOrDefault('remoteHost', env.REMOTE_HOST)
            REMOTE_PORT = config.getOrDefault('remotePort', "${env.REMOTE_PORT ?: '22'}")
            REMOTE_CREDENTIALS_ID = "${config.remoteCredentialsId ?: 'ssh-for-jenkins'}"

            BRANCH_NAME = params.getOrDefault('GIT_LABEL', env.BRANCH_NAME)
            MODULE_DIR = params.getOrDefault('MODULE_DIR', '.')

            ORGANIZATION = "${config.organization ?: 'demo'}"
            // get image mapping or use base job name or treat last path as image name
            IMAGE_NAME = "${(config.imageMapping ?: ['.' : env.JOB_BASE_NAME]).getOrDefault(env.MODULE_DIR, env.MODULE_DIR.substring(env.MODULE_DIR.lastIndexOf('/') + 1))}"
            IMAGE_WITH_TAG = "${ORGANIZATION}/${IMAGE_NAME}:${params.IMAGE_TAG}"

            // docker stack service name
            SERVICE_NAME = "${STACK_NAME}_${IMAGE_NAME}"

            // use aliyun mirror by default
            MAVEN_CENTRAL_MIRROR = "${env.MAVEN_CENTRAL_MIRROR ?: 'http://maven.aliyun.com/nexus/content/repositories/central/'}"
            MAVEN_IMAGE = "${env.MAVEN_IMAGE ?: 'maven:3.8-jdk-8-slim'}"
            MAVEN_CMD = "mvn -Duser.home=/var/maven -DskipTests -am clean package --projects ${MODULE_DIR}"

            NODE_IMAGE = "${env.NODE_IMAGE ?: 'node:14.17-slim'}"
            NODE_MIRROR = "${env.NODE_MIRROR ?: 'https://registry.npmmirror.com'}"
            NODE_RUN_CMD = "${config.nodeRunCmd ?: 'build'}"
            NODE_CMD = "cd ${MODULE_DIR} && npm install --registry=${NODE_MIRROR} && npm run ${NODE_RUN_CMD}"
        }

        stages {
            stage('Init') {
                steps {
                    echo "Module: ${MODULE_DIR}, Main Branch: ${BRANCH_NAME}, Action: ${params.ACTION}, Image tag: ${IMAGE_WITH_TAG}"
                    script {
                        // in order to trace deployment in prod, required to input a concrete tag.
                        if (env.CURRENT_ENV == 'prod' && params.ACTION ==~ '.*Deploy.*' && params.getOrDefault('IMAGE_TAG', 'latest') == 'latest') {
                            error "The latest image tag is not allowed in prod environment, please specify a concrete tag."
                        }
                    }

                    script {
                        // config maven repository mirror
                        maven_setting = '''<?xml version="1.0" encoding="UTF-8"?>
                      <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
                          <mirrors>
                            <mirror>
                                <id>customMirror</id>
                                <name>Custom maven mirror</name>
                                <url>${MAVEN_CENTRAL_MIRROR}</url>
                                <mirrorOf>central</mirrorOf>
                            </mirror>
                        </mirrors>
                      </settings>
                      '''
                        if (!fileExists("${env.HOME}/.m2/settings.xml")) {
                            writeFile file: "${env.HOME}/.m2/settings.xml", text: maven_setting, encoding: 'UTF-8'
                        }
                    }
                }
            }

            // comment Prepare stage for multi-branch pipeline
            stage('Prepare') {
                options {
                    timeout(time: 2, unit: 'MINUTES')
                }
                steps {
                    // checkout parent
                    checkout([
                            $class           : 'GitSCM',
                            branches         : [[name: env.BRANCH_NAME]], doGenerateSubmoduleConfigurations: false,
                            extensions       : [[$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true],
                                                [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]],
                            userRemoteConfigs: [[credentialsId: env.GIT_CREDENTIALS_ID, url: env.GIT_URL]]])

                    // checkout modules if specified
                    script {
                        if (params.MODULE_LABELS != "") {
                            params.MODULE_LABELS.split("[,;] *").each {
                                def (module, label) = it.split(":");
                                dir(module) {
                                    sh "git checkout ${label}"
                                }
                            }
                        }
                    }
                }
            }

            // Package for java/node
            stage('Package') {
                steps {
                    script {
                        // java project
                        // https://hub.docker.com/_/maven/
                        if (fileExists("${MODULE_DIR}/pom.xml")) {
                            docker.image(env.MAVEN_IMAGE).inside("--entrypoint= -v $HOME/.m2:/var/maven/.m2 -e MAVEN_CONFIG=/var/maven/.m2") {
                                sh "mvn --version"
                                sh MAVEN_CMD
                            }
                        }

                        // node project
                        if (fileExists("${MODULE_DIR}/package.json")) {
                            docker.image(env.NODE_IMAGE).inside("--entrypoint= -v $HOME/.npm:/home/node/.npm") {
                                sh 'node --version'
                                sh NODE_CMD
                            }
                        }
                    }
                }
            }


            stage('Build Image') {
                when {
                    expression {
                        params.ACTION ==~ '.*Build.*'
                    }
                }

                steps {
                    script {
                        // fallback dockerfile
                        dockerFile = "Dockerfile"
                        if (fileExists("${MODULE_DIR}/docker/Dockerfile")) {
                            // custom docker file
                            dockerFile = "${MODULE_DIR}/docker/Dockerfile"
                        } else if (fileExists("${MODULE_DIR}/Dockerfile")) {
                            // default docker file
                            dockerFile = "${MODULE_DIR}/Dockerfile"
                        }
                        
                        // add .dockerignore if missing
                        dockerignore = null
                        if (!fileExists("${MODULE_DIR}/.dockerignore")) {
                            dockerignore = "src/\ntarget/classes/\nnode_modules/"                           
                            writeFile file: "${MODULE_DIR}/.dockerignore", text: dockerignore, encoding: 'UTF-8'
                        }

                        // push image to registry with user/password
                        docker.withRegistry("${REGISTRY_URL}", "${REGISTRY_CREDENTIALS_ID}") {
                            def image = docker.build("${IMAGE_WITH_TAG}", "-f ${dockerFile} ${MODULE_DIR}")
                            image.push()
                            if (env.CURRENT_ENV == 'prod') {
                                image.push("latest")
                            }
                        }
                        
                        // remove added .dockerignore after building
                        if (dockerignore) {
                            sh "rm ${MODULE_DIR}/.dockerignore"
                        }
                    }
                }
            }


            stage('Deploy') {
                when {
                    expression {
                        params.ACTION ==~ '.*Deploy.*'
                    }
                }

                steps {
                    script {
                        def remote = [:]
                        remote.name = "docker-manager"
                        remote.allowAnyHosts = true
                        remote.host = "${REMOTE_HOST}"
                        remote.port = Integer.parseInt(env.REMOTE_PORT)
                        
                        def fullImage = new URL(env.REGISTRY_URL).getAuthority() + '/' + env.IMAGE_WITH_TAG

                        withCredentials([sshUserPrivateKey(
                                credentialsId: env.REMOTE_CREDENTIALS_ID,
                                keyFileVariable: 'identityFile',
                                passphraseVariable: '',
                                usernameVariable: 'remoteUser')]) {
                            remote.user = remoteUser
                            remote.identityFile = identityFile

                            // pull image, update service for testing
                            // it is recommended to copy stack yml and re-deploy stack
                            sshCommand remote: remote, command: "docker pull ${fullImage}"
                            sshCommand remote: remote, command: "docker service update --image ${fullImage} ${SERVICE_NAME}"
                        }
                    }
                }
            }
        }

        post {
            // clean workspace finally
            always {
              script {
                if (config.getOrDefault('cleanWorkspace', true)) {
                  cleanWs()
                }
              }
            }
        }
    }
}
