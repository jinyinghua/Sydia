"""
Sydia - 长期记忆模块 (RAG: Gemini Embedding + Pinecone)
"""
import asyncio
import hashlib
import os
import time
from typing import Optional

import google.generativeai as genai
from pinecone import Pinecone

EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "text-embedding-004")
PINECONE_TOP_K = int(os.getenv("PINECONE_TOP_K", "5"))

def _init_genai():
    api_key = os.getenv("GEMINI_API_KEY", "")
    if not api_key: return False
    genai.configure(api_key=api_key)
    return True

def _init_pinecone():
    api_key = os.getenv("PINECONE_API_KEY", "")
    index_host = os.getenv("PINECONE_INDEX_HOST", "")
    if not api_key or not index_host: return None
    pc = Pinecone(api_key=api_key)
    return pc.Index(host=index_host)

_genai_ready = None
_pinecone_index = None

def _ensure_init():
    global _genai_ready, _pinecone_index
    if _genai_ready is None:
        _genai_ready = _init_genai()
        _pinecone_index = _init_pinecone()
    return _genai_ready and _pinecone_index is not None

async def embed_text(text: str) -> list[float]:
    def _embed():
        result = genai.embed_content(model=f"models/{EMBEDDING_MODEL}", content=text)
        return result["embedding"]
    return await asyncio.to_thread(_embed)

def _text_to_id(text: str) -> str:
    return hashlib.md5(text.encode()).hexdigest()[:16]

async def memory_save(text: str, metadata: Optional[dict] = None) -> bool:
    if not _ensure_init(): return False
    try:
        vector = await embed_text(text)
        meta = {"text": text, "timestamp": time.time()}
        if metadata: meta.update(metadata)
        def _upsert():
            _pinecone_index.upsert(vectors=[{"id": _text_to_id(text), "values": vector, "metadata": meta}])
        await asyncio.to_thread(_upsert)
        return True
    except Exception: return False

async def memory_search_text(query: str, top_k: int = 0) -> str:
    if not _ensure_init(): return ""
    if top_k <= 0: top_k = PINECONE_TOP_K
    try:
        query_vector = await embed_text(query)
        def _query():
            return _pinecone_index.query(vector=query_vector, top_k=top_k, include_metadata=True)
        result = await asyncio.to_thread(_query)
        lines = []
        for match in result.get("matches", []):
            if match.get("score", 0) < 0.5: continue
            lines.append(f"· {match['metadata'].get('text', '')}")
        return "\n".join(lines)
    except Exception: return ""

def is_available() -> bool: return _ensure_init()
async def get_stats() -> dict:
    if not _ensure_init(): return {"available": False}
    try:
        def _stats(): return _pinecone_index.describe_index_stats()
        stats = await asyncio.to_thread(_stats)
        return {"available": True, "total_vectors": stats.get("total_vector_count", 0)}
    except Exception: return {"available": False}
