// JNI bridge for the statically-fused SQLite + sqlite-vec build.
// Contract: exposes ONLY com.janadhikar.memory.vec.SqliteVecBridge natives.
//
// SAFETY INVARIANT (CONTRIBUTING.md Rule 4): the ONLY open path in this
// library is SQLITE_OPEN_READONLY. There is no way to write to the knowledge
// base from the app process, by construction.
#include <jni.h>
#include <android/log.h>
#include "sqlite3.h"
#include "sqlite-vec.h"

#define LOG_TAG "JanadhikarSqliteVec"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Kotlin: companion object @JvmStatic → static native on the enclosing class.
JNIEXPORT jlong JNICALL
Java_com_janadhikar_memory_vec_SqliteVecBridge_nativeOpenReadOnly(
        JNIEnv *env, jclass /*clazz*/, jstring db_path) {
    const char *path = env->GetStringUTFChars(db_path, nullptr);
    sqlite3 *db = nullptr;
    int rc = sqlite3_open_v2(path, &db, SQLITE_OPEN_READONLY, nullptr);
    env->ReleaseStringUTFChars(db_path, path);
    if (rc != SQLITE_OK) {
        LOGE("open failed rc=%d: %s", rc, db ? sqlite3_errmsg(db) : "null");
        if (db) sqlite3_close_v2(db);
        return 0;
    }
    // sqlite-vec is statically linked (SQLITE_CORE); register on this connection.
    rc = sqlite3_vec_init(db, nullptr, nullptr);
    if (rc != SQLITE_OK) {
        LOGE("sqlite-vec init failed rc=%d", rc);
        sqlite3_close_v2(db);
        return 0;
    }
    LOGI("knowledge db opened read-only");
    return reinterpret_cast<jlong>(db);
}

JNIEXPORT void JNICALL
Java_com_janadhikar_memory_vec_SqliteVecBridge_nativeClose(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong db_ptr) {
    if (db_ptr != 0) {
        sqlite3_close_v2(reinterpret_cast<sqlite3 *>(db_ptr));
    }
}

// KNN over vec_chunks. Fills outIds/outDistances (caller-allocated, length k),
// returns the number of neighbours found, or -1 on error.
JNIEXPORT jint JNICALL
Java_com_janadhikar_memory_vec_SqliteVecBridge_nativeVectorSearch(
        JNIEnv *env, jobject /*thiz*/, jlong db_ptr,
        jfloatArray query, jint k, jlongArray out_ids, jfloatArray out_distances) {
    auto *db = reinterpret_cast<sqlite3 *>(db_ptr);
    if (db == nullptr || k <= 0) return -1;

    static const char *SQL =
            "SELECT chunk_id, distance FROM vec_chunks "
            "WHERE embedding MATCH ?1 AND k = ?2 ORDER BY distance";

    sqlite3_stmt *stmt = nullptr;
    if (sqlite3_prepare_v2(db, SQL, -1, &stmt, nullptr) != SQLITE_OK) {
        LOGE("prepare failed: %s", sqlite3_errmsg(db));
        return -1;
    }

    jsize dim = env->GetArrayLength(query);
    jfloat *query_data = env->GetFloatArrayElements(query, nullptr);
    // sqlite-vec accepts a raw little-endian float32 blob as the query vector.
    sqlite3_bind_blob(stmt, 1, query_data, static_cast<int>(dim * sizeof(float)),
                      SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 2, k);
    env->ReleaseFloatArrayElements(query, query_data, JNI_ABORT);

    jlong *ids = env->GetLongArrayElements(out_ids, nullptr);
    jfloat *dists = env->GetFloatArrayElements(out_distances, nullptr);

    int found = 0;
    while (found < k && sqlite3_step(stmt) == SQLITE_ROW) {
        ids[found] = sqlite3_column_int64(stmt, 0);
        dists[found] = static_cast<jfloat>(sqlite3_column_double(stmt, 1));
        found++;
    }
    sqlite3_finalize(stmt);

    env->ReleaseLongArrayElements(out_ids, ids, 0);
    env->ReleaseFloatArrayElements(out_distances, dists, 0);
    return found;
}

} // extern "C"
