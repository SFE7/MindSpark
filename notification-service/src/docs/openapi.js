export const openApiSpec = {
  openapi: '3.0.3',
  info: {
    title: 'MindSpark Notification Service API',
    description: 'REST API for user notification management in MindSpark (Express.js + SQLite + Kafka)',
    version: '1.0.0'
  },
  servers: [
    { url: '/api/notifications', description: 'Notification Service Base Path' }
  ],
  components: {
    securitySchemes: {
      bearerAuth: {
        type: 'http',
        scheme: 'bearer',
        bearerFormat: 'JWT',
        description: 'Keycloak JWT Bearer Token'
      }
    },
    schemas: {
      Notification: {
        type: 'object',
        properties: {
          id: { type: 'string', format: 'uuid' },
          eventId: { type: 'string' },
          userId: { type: 'string' },
          eventType: { type: 'string' },
          title: { type: 'string' },
          message: { type: 'string' },
          actionUrl: { type: 'string', nullable: true },
          metadata: { type: 'object', nullable: true },
          isRead: { type: 'boolean' },
          createdAt: { type: 'string', format: 'date-time' }
        }
      },
      NotificationList: {
        type: 'object',
        properties: {
          items: {
            type: 'array',
            items: { $ref: '#/components/schemas/Notification' }
          },
          unreadCount: { type: 'integer' }
        }
      },
      UnreadCount: {
        type: 'object',
        properties: {
          unreadCount: { type: 'integer' }
        }
      },
      HealthResponse: {
        type: 'object',
        properties: {
          status: { type: 'string', example: 'UP' },
          service: { type: 'string', example: 'notification-service' },
          framework: { type: 'string', example: 'express' },
          kafkaConnected: { type: 'boolean' },
          eurekaRegistered: { type: 'boolean' }
        }
      },
      ErrorResponse: {
        type: 'object',
        properties: {
          error: { type: 'string' },
          message: { type: 'string' }
        }
      }
    }
  },
  paths: {
    '/health': {
      get: {
        summary: 'Service health check',
        description: 'Returns the health status of the notification service including Kafka and Eureka connectivity',
        responses: {
          '200': {
            description: 'Health status',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/HealthResponse' }
              }
            }
          }
        }
      }
    },
    '/': {
      get: {
        summary: 'List user notifications',
        description: 'Returns notifications belonging to the authenticated user',
        security: [{ bearerAuth: [] }],
        parameters: [
          {
            name: 'limit',
            in: 'query',
            description: 'Maximum number of items to return (1-100)',
            schema: { type: 'integer', default: 30 }
          },
          {
            name: 'unreadOnly',
            in: 'query',
            description: 'Filter unread notifications only',
            schema: { type: 'boolean', default: false }
          }
        ],
        responses: {
          '200': {
            description: 'List of notifications and unread count',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/NotificationList' }
              }
            }
          },
          '401': {
            description: 'Unauthorized',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/ErrorResponse' }
              }
            }
          }
        }
      }
    },
    '/unread-count': {
      get: {
        summary: 'Get unread notification count',
        description: 'Returns the count of unread notifications for the authenticated user',
        security: [{ bearerAuth: [] }],
        responses: {
          '200': {
            description: 'Unread count',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/UnreadCount' }
              }
            }
          },
          '401': {
            description: 'Unauthorized'
          }
        }
      }
    },
    '/{id}/read': {
      patch: {
        summary: 'Mark notification as read',
        description: 'Marks a specific notification owned by the authenticated user as read',
        security: [{ bearerAuth: [] }],
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'string', format: 'uuid' }
          }
        ],
        responses: {
          '200': {
            description: 'Notification marked read',
            content: {
              'application/json': {
                schema: { $ref: '#/components/schemas/Notification' }
              }
            }
          },
          '404': {
            description: 'Notification not found or not owned by user'
          }
        }
      }
    },
    '/read-all': {
      patch: {
        summary: 'Mark all notifications read',
        description: 'Marks all unread notifications of the authenticated user as read',
        security: [{ bearerAuth: [] }],
        responses: {
          '200': {
            description: 'Count of updated notifications',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    updatedCount: { type: 'integer' }
                  }
                }
              }
            }
          }
        }
      }
    },
    '/{id}': {
      delete: {
        summary: 'Delete notification',
        description: 'Deletes a notification owned by the authenticated user',
        security: [{ bearerAuth: [] }],
        parameters: [
          {
            name: 'id',
            in: 'path',
            required: true,
            schema: { type: 'string', format: 'uuid' }
          }
        ],
        responses: {
          '204': {
            description: 'Notification deleted successfully'
          },
          '404': {
            description: 'Notification not found or not owned by user'
          }
        }
      }
    }
  }
};
