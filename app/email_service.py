"""
Sydia - 双向邮件通信模块 (SMTP 发信 / IMAP 收信)
Agent 像真人一样使用电子邮件：主动汇报、求助、接收指令
"""
import asyncio
import imaplib
import smtplib
import email as email_lib
import os
import time
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.header import decode_header


def _get_config():
    return {
        "account": os.getenv("EMAIL_ACCOUNT", ""),
        "password": os.getenv("EMAIL_PASSWORD", ""),
        "smtp_host": os.getenv("SMTP_HOST", "smtp.qq.com"),
        "smtp_port": int(os.getenv("SMTP_PORT", "465")),
        "imap_host": os.getenv("IMAP_HOST", "imap.qq.com"),
        "imap_port": int(os.getenv("IMAP_PORT", "993")),
        "trusted_sender": os.getenv("TRUSTED_SENDER", ""),
    }


# ─────────── SMTP: Agent 主动发邮件 ───────────

async def send_email(to_addr: str, subject: str, content: str) -> bool:
    """Agent 像真人一样发送邮件"""
    cfg = _get_config()
    if not cfg["account"] or not cfg["password"]:
        print("[Email] 未配置邮箱账号, 跳过发送")
        return False

    if not to_addr:
        to_addr = cfg["trusted_sender"]

    msg = MIMEMultipart("alternative")
    msg["From"] = cfg["account"]
    msg["To"] = to_addr
    msg["Subject"] = subject

    # 同时发送纯文本和 HTML 格式
    text_part = MIMEText(content, "plain", "utf-8")
    html_content = f"""
    <div style="font-family:system-ui;padding:20px;background:#181212;color:#ece0df;border-radius:12px;">
        <h2 style="color:#ffb3af;">🤖 Sydia Agent</h2>
        <div style="white-space:pre-wrap;line-height:1.6;">{content}</div>
        <hr style="border-color:#594847;margin:16px 0;">
        <small style="color:#988e8e;">此邮件由 Sydia AI Agent 自动发送</small>
    </div>
    """
    html_part = MIMEText(html_content, "html", "utf-8")
    msg.attach(text_part)
    msg.attach(html_part)

    def _send():
        try:
            with smtplib.SMTP_SSL(cfg["smtp_host"], cfg["smtp_port"]) as server:
                server.login(cfg["account"], cfg["password"])
                server.sendmail(cfg["account"], [to_addr], msg.as_string())
            return True
        except Exception as e:
            print(f"[Email] 发送失败: {e}")
            return False

    result = await asyncio.to_thread(_send)
    if result:
        print(f"[Email] ✉️ 邮件已发送 -> {to_addr} | 主题: {subject}")
    return result


# ─────────── IMAP: Agent 主动收邮件 ───────────

async def fetch_new_emails(mark_read: bool = True) -> list[dict]:
    """
    从受信任发件人获取未读邮件
    返回: [{"from": str, "subject": str, "body": str, "date": str}, ...]
    """
    cfg = _get_config()
    if not cfg["account"] or not cfg["password"]:
        return []

    def _fetch():
        results = []
        try:
            mail = imaplib.IMAP4_SSL(cfg["imap_host"], cfg["imap_port"])
            mail.login(cfg["account"], cfg["password"])
            mail.select("INBOX")

            # 搜索条件: 未读邮件; 如果配置了信任发件人则限定来源
            search_criteria = "UNSEEN"
            if cfg["trusted_sender"]:
                search_criteria = f'(UNSEEN FROM "{cfg["trusted_sender"]}")'

            status, msg_nums = mail.search(None, search_criteria)
            if status != "OK" or not msg_nums[0]:
                mail.logout()
                return results

            for num in msg_nums[0].split():
                status, data = mail.fetch(num, "(RFC822)")
                if status != "OK":
                    continue

                msg = email_lib.message_from_bytes(data[0][1])

                # 解码主题
                raw_subject = decode_header(msg["Subject"] or "")
                subject_parts = []
                for part, charset in raw_subject:
                    if isinstance(part, bytes):
                        subject_parts.append(part.decode(charset or "utf-8", errors="replace"))
                    else:
                        subject_parts.append(part)
                subject = "".join(subject_parts)

                # 解码发件人
                sender = msg.get("From", "unknown")

                # 提取正文
                body = ""
                if msg.is_multipart():
                    for part in msg.walk():
                        ctype = part.get_content_type()
                        if ctype == "text/plain":
                            payload = part.get_payload(decode=True)
                            if payload:
                                charset = part.get_content_charset() or "utf-8"
                                body = payload.decode(charset, errors="replace")
                                break
                else:
                    payload = msg.get_payload(decode=True)
                    if payload:
                        charset = msg.get_content_charset() or "utf-8"
                        body = payload.decode(charset, errors="replace")

                results.append({
                    "from": sender,
                    "subject": subject.strip(),
                    "body": body.strip(),
                    "date": msg.get("Date", ""),
                })

                # 标记为已读
                if mark_read:
                    mail.store(num, "+FLAGS", "\\Seen")

            mail.logout()
        except Exception as e:
            print(f"[Email] 收信失败: {e}")
        return results

    return await asyncio.to_thread(_fetch)
