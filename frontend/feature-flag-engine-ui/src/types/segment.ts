export type SegmentRuleOperator =
  | 'equals'
  | 'not_equals'
  | 'contains'
  | 'in'
  | 'greater_than'
  | 'less_than';

export interface SegmentRule {
  attribute: string;
  operator: SegmentRuleOperator;
  value: string;
}

export interface Segment {
  id: string;
  name: string;
  key?: string;
  description?: string;
  projectId: string;
  projectName?: string;
  rules: SegmentRule[];
  createdAt?: string;
}

export interface SegmentInput {
  name: string;
  key?: string;
  description?: string;
  projectId: string;
  rules: SegmentRule[];
}
