import logging
from fastapi import FastAPI
from pydantic import BaseModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("NotificationsService")

app = FastAPI(title="Notifications Service")

class NotificationRequest(BaseModel):
    user_id: int
    message: str

@app.post("/api/v1/notifications")
def send_notification(notification: NotificationRequest):
    logger.info(f"[NOTIFICATION SENT] User ID {notification.user_id}: {notification.message}")
    return {"status": "success", "user_id": notification.user_id, "delivered": True}

@app.get("/health")
def health_check():
    return {"status": "UP"}
