from pathlib import Path
p=Path(__file__).resolve().parents[1]/'contracts/openapi/synqoria-m03-triage-restricted-v1.yaml'
s=p.read_text().replace('version: 0.56.0','version: 0.57.0')
needle='components:\n  securitySchemes:'
paths='''  /api/v1/triage/patients/{patientId}/cases/{caseId}:
    get:
      summary: Authorized minimal case state; never clinical free text
      security:
      - bearerAuth: []
      parameters: *id001
      responses:
        '200':
          description: Patient-scoped summary, no-store private
          headers:
            Cache-Control:
              schema:
                type: string
                enum: ['no-store, private']
          content:
            application/json:
              schema:
                type: object
                additionalProperties: false
                required: [case]
                properties:
                  case:
                    $ref: '#/components/schemas/TriageCaseSummary'
        '400': {description: Invalid request}
        '401': {description: Verified session required}
        '404': {description: Patient or case not disclosed}
        '503': {description: Service unavailable}
  /api/v1/triage/patients/{patientId}/cases/{caseId}/history:
    get:
      summary: Patient-scoped bounded audit page; no patient note content
      security:
      - bearerAuth: []
      parameters:
      - name: patientId
        in: path
        required: true
        schema: {type: string, format: uuid}
      - name: caseId
        in: path
        required: true
        schema: {type: string, format: uuid}
      - name: afterRevision
        in: query
        required: false
        schema: {type: integer, minimum: 0, maximum: 2147483647}
      responses:
        '200':
          description: At most 100 strictly increasing audit events with revision cursor
          headers:
            Cache-Control:
              schema:
                type: string
                enum: ['no-store, private']
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TriageAuditPage'
        '400': {description: Invalid request}
        '401': {description: Verified session required}
        '404': {description: Patient or case not disclosed}
        '503': {description: Service unavailable}
'''
s=s.replace(needle,paths+needle)
append='''    TriageCaseSummary:
      type: object
      additionalProperties: false
      required: [caseId, stage, revision, station, assignee, arrivedAt, paused, safetyHold, manualEscalation]
      properties:
        caseId: {type: string, format: uuid}
        stage:
          type: string
          enum: [ARRIVED, WAITING, CALLED, ASSESSING, ASSESSED, PRIORITIZED, ASSIGNED, HANDED_OVER, CLOSED]
        revision: {type: integer, minimum: 1, maximum: 2147483647}
        station: {type: string, format: uuid, nullable: true}
        assignee: {type: string, format: uuid, nullable: true}
        arrivedAt: {type: string, format: date-time, description: UTC instant}
        paused: {type: boolean}
        safetyHold: {type: boolean}
        manualEscalation: {type: boolean}
    TriageAuditEvent:
      type: object
      additionalProperties: false
      required: [revision, operation, occurredAt, digest]
      properties:
        revision: {type: integer, minimum: 1, maximum: 2147483647}
        operation: {type: string, pattern: '^[A-Z_]{2,80}$'}
        occurredAt: {type: string, format: date-time, description: UTC instant}
        digest: {type: string, pattern: '^[0-9a-f]{64}$'}
    TriageAuditPage:
      type: object
      additionalProperties: false
      required: [history, nextAfterRevision]
      properties:
        history:
          type: array
          maxItems: 100
          items: {$ref: '#/components/schemas/TriageAuditEvent'}
        nextAfterRevision: {type: integer, minimum: 1, maximum: 2147483647, nullable: true}
'''
s=s.rstrip()+'\n'+append
p.write_text(s)
