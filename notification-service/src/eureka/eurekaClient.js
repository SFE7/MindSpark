export class EurekaClient {
  constructor(config) {
    this.config = config;
    this.registered = false;
    this.heartbeatTimer = null;
    this.retryTimer = null;
    this.instanceId = `${config.eurekaHostName}:${config.port}`;
  }

  async register() {
    const url = `${this.config.eurekaUrl}/apps/${this.config.eurekaAppName}`;
    const payload = {
      instance: {
        instanceId: this.instanceId,
        hostName: this.config.eurekaHostName,
        app: this.config.eurekaAppName,
        ipAddr: this.config.eurekaIpAddr,
        status: 'UP',
        port: { $: this.config.port, '@enabled': 'true' },
        securePort: { $: 443, '@enabled': 'false' },
        vipAddress: 'notification-service',
        secureVipAddress: 'notification-service',
        healthCheckUrl: `http://${this.config.eurekaHostName}:${this.config.port}/api/notifications/health`,
        statusPageUrl: `http://${this.config.eurekaHostName}:${this.config.port}/api/notifications/health`,
        homePageUrl: `http://${this.config.eurekaHostName}:${this.config.port}/`,
        dataCenterInfo: {
          '@class': 'com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo',
          name: 'MyOwn'
        },
        leaseInfo: {
          renewalIntervalInSecs: 10,
          durationInSecs: 30
        }
      }
    };

    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(payload)
      });

      if (res.status === 204 || res.status === 200) {
        this.registered = true;
        console.log(`[Eureka] Successfully registered instance ${this.instanceId} with ${this.config.eurekaUrl}`);
        this.startHeartbeat();
      } else {
        console.warn(`[Eureka] Registration returned HTTP ${res.status}`);
        this.scheduleRetry();
      }
    } catch (err) {
      this.registered = false;
      console.warn(`[Eureka] Registration failed: ${err.message}`);
      this.scheduleRetry();
    }
  }

  startHeartbeat() {
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = setInterval(() => this.heartbeat(), 15000);
  }

  async heartbeat() {
    const url = `${this.config.eurekaUrl}/apps/${this.config.eurekaAppName}/${this.instanceId}`;
    try {
      const res = await fetch(url, {
        method: 'PUT',
        headers: { 'Accept': 'application/json' }
      });

      if (res.status === 200) {
        this.registered = true;
      } else if (res.status === 404) {
        console.warn('[Eureka] Heartbeat returned 404 - instance expired, re-registering...');
        this.register();
      }
    } catch (err) {
      this.registered = false;
      console.warn(`[Eureka] Heartbeat ping failed: ${err.message}`);
    }
  }

  scheduleRetry() {
    if (this.retryTimer) return;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.register();
    }, 10000);
  }

  async deregister() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    const url = `${this.config.eurekaUrl}/apps/${this.config.eurekaAppName}/${this.instanceId}`;
    try {
      await fetch(url, { method: 'DELETE' });
      this.registered = false;
      console.log(`[Eureka] Deregistered instance ${this.instanceId}`);
    } catch (e) {}
  }

  isRegistered() {
    return this.registered;
  }
}
