package cn.ifafu.ifafu.service

import cn.ifafu.ifafu.bean.dto.IFResponse
import cn.ifafu.ifafu.entity.Holiday
import cn.ifafu.ifafu.entity.FirstWeek
import retrofit2.http.*

interface IFAFUService {

    @GET("/public/holiday")
    suspend fun holiday(): List<Holiday>

    @GET("/public/firstWeeks")
    suspend fun firstWeeks(): List<FirstWeek>
}