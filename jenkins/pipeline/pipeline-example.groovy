// Jenkins pipeline example
// Define shared lib as per following
// https://www.jenkins.io/doc/book/pipeline/shared-libraries/

@Library('custom-library') _

buildPipeline([
        gitUrl: "your git url",
        registryUrl: "left blank to use docker hub",
        stackName: 'app',
        organization: 'demo',
        imageMapping: ['.' : 'test-image']
])