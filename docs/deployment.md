# 部署指南

## 需求

- Java 21+（JRE 即可，不需要完整 JDK）

## 取得 jar 檔

從 [GitHub Releases](../../releases) 下載最新的 `standalone.jar`。

或自行從原始碼 build：

```bash
bb build
# 產出：target/standalone.jar
```

## 環境變數

啟動前必須設定以下三個環境變數：

| 變數名稱 | 說明 | 範例 |
|----------|------|------|
| `SESSION_SECRET_KEY` | Session 加密金鑰，請使用隨機長字串 | `openssl rand -hex 32` |
| `DB_PATH` | SQLite DB 檔案的絕對路徑 | `/var/lib/crm/crm.db` |
| `PORT` | 伺服器監聽的 port | `8080` |

`SESSION_SECRET_KEY` 必須夠長、夠隨機，且在每個環境保持固定——若變更，所有使用者的登入 session 會失效。

## 啟動

```bash
export SESSION_SECRET_KEY=your-secret-key
export DB_PATH=/var/lib/crm/crm.db
export PORT=8080

java -jar standalone.jar
```

首次啟動時，系統會自動對 DB 執行 migrations，建立所有資料表。

## 設定為系統服務（systemd）

建立 `/etc/systemd/system/lite-crm.service`：

```ini
[Unit]
Description=Lite CRM
After=network.target

[Service]
User=crm
WorkingDirectory=/opt/lite-crm
ExecStart=java -jar /opt/lite-crm/standalone.jar
Restart=on-failure
RestartSec=5

Environment=SESSION_SECRET_KEY=your-secret-key
Environment=DB_PATH=/var/lib/crm/crm.db
Environment=PORT=8080

[Install]
WantedBy=multi-user.target
```

啟用並啟動：

```bash
sudo systemctl enable lite-crm
sudo systemctl start lite-crm
sudo systemctl status lite-crm
```

## 備份 DB

SQLite 備份用 `.backup` 指令，可在 app 運行中執行，不影響寫入一致性：

```bash
sqlite3 /var/lib/crm/crm.db ".backup /backup/crm-$(date +%Y%m%d).db"
```

建議加入 crontab，每日自動備份：

```bash
# crontab -e
0 3 * * * sqlite3 /var/lib/crm/crm.db ".backup /backup/crm-$(date +\%Y\%m\%d).db"
```

## 升級版本

1. 下載新版 `standalone.jar`
2. 停止服務：`sudo systemctl stop lite-crm`
3. 替換 jar 檔
4. 啟動服務：`sudo systemctl start lite-crm`

Migrations 會在啟動時自動執行，只會套用尚未執行過的版本。

## 反向代理（選用）

若要使用 80/443 port 或 HTTPS，建議在前面掛 nginx：

```nginx
server {
    listen 80;
    server_name crm.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```
