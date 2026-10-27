/** BPMN export metadata. The server still validates and stores these values in relational tables. */
export default {
  name: 'EAM',
  uri: 'https://eam.local/workflow',
  prefix: 'eam',
  types: [
    {
      name: 'WorkflowProperties',
      extends: ['bpmn:BaseElement'],
      properties: ['approvalMode', 'users', 'roles', 'branchKey', 'outputKey', 'outputType'].map(
        (name) => ({ name, isAttr: true, type: 'String' }),
      ),
    },
  ],
};
