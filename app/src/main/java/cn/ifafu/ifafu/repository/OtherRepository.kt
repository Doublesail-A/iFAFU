package cn.ifafu.ifafu.repository

import cn.ifafu.ifafu.bean.vo.Weather
import cn.ifafu.ifafu.exception.IFResponseFailureException
import cn.ifafu.ifafu.service.WeatherService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class OtherRepository @Inject constructor(
    private val weatherService: WeatherService,
) {

    fun getWeather(code: String): Flow<Weather> = flow {
        val resp = weatherService.getWeather(code)
        if (resp.isSuccess()) {
            emit(resp.data ?: throw IFResponseFailureException("获取天气出错"))
        } else {
            throw IFResponseFailureException(resp.message)
        }
    }

}