// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
use jni::objects::{JObject, JString};
use jni::sys::{jlong, jlongArray};
use jni::JNIEnv;
use std::ptr;
use tokenizers::{Tokenizer, TruncationParams};

fn error(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new("java/lang/IllegalStateException", message);
}

#[no_mangle]
pub extern "system" fn Java_app_meltype_onnxprobe_LocalTokenizer_nativeCreate(
    mut env: JNIEnv,
    _this: JObject,
    path: JString,
) -> jlong {
    let path: String = match env.get_string(&path) {
        Ok(value) => value.into(),
        Err(_) => { error(&mut env, "Cannot read tokenizer path"); return 0; }
    };
    let mut tokenizer = match Tokenizer::from_file(path) {
        Ok(value) => value,
        Err(_) => { error(&mut env, "Cannot load tokenizer"); return 0; }
    };
    tokenizer.with_padding(None);
    if tokenizer.with_truncation(Some(TruncationParams { max_length: 48, ..Default::default() })).is_err() {
        error(&mut env, "Cannot configure tokenizer");
        return 0;
    }
    Box::into_raw(Box::new(tokenizer)) as jlong
}

#[no_mangle]
pub extern "system" fn Java_app_meltype_onnxprobe_LocalTokenizer_nativeEncode(
    mut env: JNIEnv,
    _this: JObject,
    handle: jlong,
    text: JString,
) -> jlongArray {
    if handle == 0 {
        error(&mut env, "Tokenizer is closed");
        return ptr::null_mut();
    }
    let text: String = match env.get_string(&text) {
        Ok(value) => value.into(),
        Err(_) => { error(&mut env, "Cannot read input"); return ptr::null_mut(); }
    };
    // LocalTokenizer serializes encode and close calls around this native handle.
    let tokenizer = unsafe { &*(handle as *const Tokenizer) };
    let encoded = match tokenizer.encode(text, true) {
        Ok(value) => value,
        Err(_) => { error(&mut env, "Tokenization failed"); return ptr::null_mut(); }
    };
    let ids: Vec<jlong> = encoded.get_ids().iter().map(|&value| value as jlong).collect();
    let array = match env.new_long_array(ids.len() as i32) {
        Ok(value) => value,
        Err(_) => return ptr::null_mut(),
    };
    if env.set_long_array_region(&array, 0, &ids).is_err() { return ptr::null_mut(); }
    array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_app_meltype_onnxprobe_LocalTokenizer_nativeClose(
    _env: JNIEnv,
    _this: JObject,
    handle: jlong,
) {
    if handle != 0 {
        // The Kotlin owner releases its handle only once.
        drop(unsafe { Box::from_raw(handle as *mut Tokenizer) });
    }
}
