
> port 9104, grafana: MySQL Overview

1.  Grant mysql user `exporter`
    
    ```sql
    GRANT REPLICATION CLIENT,PROCESS,SELECT ON *.* TO exporter@'127.0.0.1' identified by 'exporter-pwd';
    ```
    
2.  Download `mysqld_exporter` from https://prometheus.io/download/ and unzip to `/opt`
    
3.  Create `/opt/mysqld_exporter/.my.cnf`
    
    ```cnf
    [client]
    host=127.0.0.1
    user=exporter
    password=exporter-pwd
    port=3306
    ```
    
4.  Create `/etc/systemd/system/mysqld_exporter.service`
    
    ```service
    [Unit]
    Description=Mysqld exporter
    [Service]
    ExecStart=/opt/mysqld_exporter/mysqld_exporter --config.my-cnf=/opt/mysqld_exporter/.my.cnf
    Restart=on-failure
    [Install]
    WantedBy=multi-user.target
    ```
    
5.  Start service
    
    ```sh
    systemctl daemon-reload
    systemctl restart mysqld_exporter.service
    systemctl enable mysqld_exporter.service
    systemctl status mysqld_exporter.service
    ```
    
