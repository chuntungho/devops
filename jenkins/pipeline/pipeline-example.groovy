// Jenkins pipeline example
// Define shared lib as per following
// https://www.jenkins.io/doc/book/pipeline/shared-libraries/

@Library('custom-library') _

buildPipeline([
        currentEnv: 'test',
        stackName: 'app',
        
        gitUrl: "your git url",
        registryUrl: "left blank to use docker hub",
        
        organization: 'demo',
        imageMapping: ['.' : 'test-image']
])