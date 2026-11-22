package com.mindspark.compose.core.network

import retrofit2.HttpException
import java.io.IOException

fun isConnectionError(t: Throwable): Boolean = t is IOException

fun httpCode(t: Throwable): Int? = (t as? HttpException)?.code()
