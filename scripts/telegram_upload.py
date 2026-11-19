#!/usr/bin/env python3
import os
import sys
import asyncio
from pathlib import Path
from telegram import Bot
from telegram.constants import ParseMode
from telegram.error import RetryAfter, TelegramError

BOT_TOKEN = os.environ.get("BOT_TOKEN")
CHAT_ID = os.environ.get("CHAT_ID")
RUN_URL = os.environ.get("RUN_URL", "")
RUN_NUMBER = os.environ.get("RUN_NUMBER", "N/A")
BRANCH = os.environ.get("BRANCH", "main")
COMMIT_MSG = os.environ.get("COMMIT_MSG", "N/A")

def escape_html(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

async def upload_file(apk_path: str):
    if not BOT_TOKEN or not CHAT_ID:
        print("[-] Error: BOT_TOKEN or CHAT_ID environment variable is missing!")
        sys.exit(1)

    file_path = Path(apk_path)
    if not file_path.is_file():
        print(f"[-] Error: File not found: {file_path.resolve()}")
        sys.exit(1)

    file_size_mb = file_path.stat().st_size / (1024 * 1024)
    print(f"[+] Target APK: {file_path.name} ({file_size_mb:.2f} MB)")

    bot = Bot(token=BOT_TOKEN)

    # Format caption
    caption = (
        f"🚀 <b>Crest Editor Build #{escape_html(str(RUN_NUMBER))}</b>\n"
        f"🌿 <b>Branch:</b> <code>{escape_html(BRANCH)}</code>\n"
        f"📝 <b>Commit:</b> <code>{escape_html(COMMIT_MSG)}</code>\n\n"
        f"🔗 <a href='{escape_html(RUN_URL)}'>Workflow Run</a>"
    )

    max_attempts = 3
    for attempt in range(1, max_attempts + 1):
        try:
            print(f"[+] Uploading to Telegram (Attempt {attempt}/{max_attempts})...")
            with open(file_path, "rb") as f:
                msg = await bot.send_document(
                    chat_id=int(CHAT_ID),
                    document=f,
                    filename=file_path.name,
                    caption=caption,
                    parse_mode=ParseMode.HTML,
                    read_timeout=300,
                    write_timeout=300,
                    connect_timeout=60,
                )
            print(f"[✓] Successfully sent to Telegram! Message ID: {msg.message_id}")
            return
        except RetryAfter as e:
            print(f"[!] Hit Telegram flood limit, waiting {e.retry_after} seconds...")
            await asyncio.sleep(e.retry_after)
        except TelegramError as e:
            print(f"[-] Telegram Error (Attempt {attempt}): {e}")
            if attempt < max_attempts:
                await asyncio.sleep(3)
            else:
                raise
        except Exception as e:
            print(f"[-] Unexpected Error (Attempt {attempt}): {e}")
            if attempt < max_attempts:
                await asyncio.sleep(3)
            else:
                raise

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 telegram_upload.py <path_to_apk>")
        sys.exit(1)

    asyncio.run(upload_file(sys.argv[1]))
