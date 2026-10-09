package com.ibkglobal.integrator.api.rest.reportmanager.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import com.ibkglobal.common.convert.Converter;
import com.ibkglobal.common.repository.CacheType;
import com.ibkglobal.common.repository.service.CacheService;
import com.ibkglobal.integrator.exception.ApiException;
import com.ibkglobal.integrator.exception.model.ResultResponse;

@Service
public class ReportProcessService {

	@Autowired
	CacheService cacheService;
	
	@Autowired
	Converter converter;
	
	public ResultResponse getMsgListPage(String cacheType, String intfId, String pageNo, String pageSize) throws Exception {
		try {
			int startRow = ((Integer.parseInt(pageNo) - 1) * Integer.parseInt(pageSize));
			int endRow = Integer.parseInt(pageNo) * Integer.parseInt(pageSize);
			
			List<Object> retList = getMsgList(cacheType, intfId);
			
			int totalCnt = retList.size();
			endRow = totalCnt < endRow ? totalCnt : endRow;
			
			return new ResultResponse(retList.subList(startRow, endRow), totalCnt);
		} catch (Exception ex) {
			throw new ApiException("getMsgLisgPage Exception: " + ex.getMessage());
		}
	}

	public List<Object> getMsgList(String cacheType, String intfId) throws Exception {

		final List<Object> msgList = new ArrayList<>();

		switch (cacheType) {
		case "interface":
			try {
				List<Object> tempList = cacheService.findAll(CacheType.IBK_MCA_INTERFACE);

				if (tempList != null) {
					tempList.stream().forEach(t -> {
						try {
							Field fIntfId = t.getClass().getDeclaredField("intfId");
							
							ReflectionUtils.makeAccessible(fIntfId);

							if (fIntfId.get(t).toString().toUpperCase().contains(intfId.toUpperCase())) {
								msgList.add(t);
							}

						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
			} catch (Exception ex) {
				throw new ApiException("MCA Interface 캐시 파싱 오류: " + ex.getMessage());
			}

			break;
		case "sourceIo":
			try {
				List<Object> tempList = cacheService.findAll(CacheType.IBK_SOURCE_IO);

				if (tempList != null) {
					tempList.stream().forEach(t -> {
						try {
							Field fIntfId = t.getClass().getDeclaredField("inopId");
							
							ReflectionUtils.makeAccessible(fIntfId);

							if (fIntfId.get(t).toString().toUpperCase().contains(intfId.toUpperCase())) {
								msgList.add(t);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}

			} catch (Exception ex) {
				throw new ApiException("MCA SourceIo 캐시 파싱 오류: " + ex.getMessage());
			}

			break;
		case "targetIo":
			try {
				List<Object> tempList = cacheService.findAll(CacheType.IBK_TARGET_IO);

				if (tempList != null) {
					tempList.stream().forEach(t -> {
						Field fIntfId;
						try {
							fIntfId = t.getClass().getDeclaredField("inopId");
							
							ReflectionUtils.makeAccessible(fIntfId);

							if (fIntfId.get(t).toString().toUpperCase().contains(intfId.toUpperCase())) {
								msgList.add(t);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
			} catch (Exception ex) {
				throw new ApiException("MCA TargetIo 캐시 파싱 오류: " + ex.getMessage());
			}

			break;
		case "inMapping":
			try {
				List<Object> tempList = cacheService.findAll(CacheType.IBK_MAPPING_IN);

				if (tempList != null) {
					tempList.stream().forEach(t -> {
						try {
							Field fIntfId = t.getClass().getDeclaredField("intfId");
							
							ReflectionUtils.makeAccessible(fIntfId);

							if (fIntfId.get(t).toString().toUpperCase().contains(intfId.toUpperCase())) {
								String sIntfId = fIntfId.get(t).toString();

								Field fIntfIdnNm = t.getClass().getDeclaredField("intfIdnNm");
								
								ReflectionUtils.makeAccessible(fIntfIdnNm);

								Object tIntfInfo = cacheService.find(CacheType.IBK_MCA_INTERFACE, sIntfId);

								if (tIntfInfo != null) {
									Field fIntfNm = tIntfInfo.getClass().getDeclaredField("intfNm");
									
									ReflectionUtils.makeAccessible(fIntfNm);

									String sIntfNm = fIntfNm.get(tIntfInfo).toString();

									fIntfIdnNm.set(t, sIntfNm);
								}

								msgList.add(t);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
			} catch (Exception ex) {
				throw new ApiException("MCA InMapping 캐시 파싱 오류: " + ex.getMessage());
			}

			break;
		case "outMapping":
			try {
				List<Object> tempList = cacheService.findAll(CacheType.IBK_MAPPING_OUT);

				if (tempList != null) {
					tempList.stream().forEach(t -> {
						try {
							Field fIntfId = t.getClass().getDeclaredField("intfId");
							
							ReflectionUtils.makeAccessible(fIntfId);

							if (fIntfId.get(t).toString().toUpperCase().contains(intfId.toUpperCase())) {
								String sIntfId = fIntfId.get(t).toString();

								Field fIntfIdnNm = t.getClass().getDeclaredField("intfIdnNm");
								
								ReflectionUtils.makeAccessible(fIntfIdnNm);

								Object tIntfInfo = cacheService.find(CacheType.IBK_MCA_INTERFACE, sIntfId);

								if (tIntfInfo != null) {
									Field fIntfNm = tIntfInfo.getClass().getDeclaredField("intfNm");
									
									ReflectionUtils.makeAccessible(fIntfNm);

									String sIntfNm = fIntfNm.get(tIntfInfo).toString();

									fIntfIdnNm.set(t, sIntfNm);
								}

								msgList.add(t);
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					});
				}
			} catch (Exception ex) {
				throw new ApiException("MCA OutMapping 캐시 파싱 오류: " + ex.getMessage());
			}
			
			break;
		default:
			break;
		}
		
		return msgList;
	}
	
	public ResultResponse getMsgInfo(String cacheType, String intfId) throws Exception {
		Object tCacheInfo = new HashMap<>();
		
		switch (cacheType) {
			case "interface": 
				try {
					tCacheInfo = cacheService.find(CacheType.IBK_MCA_INTERFACE, intfId);
				} catch (Exception ex) {
					throw new ApiException("MCA Interface 캐시 파싱 오류[" + intfId + "]: " + ex.getMessage());
				}
				
				break;
			case "sourceIo":
				try {
					tCacheInfo = cacheService.find(CacheType.IBK_SOURCE_IO, intfId);
				} catch (Exception ex) {
					throw new ApiException("MCA SourceIo 캐시 파싱 오류[" + intfId + "]: " + ex.getMessage());
				}
				
				break;
			case "targetIo": 
				try {
					tCacheInfo = cacheService.find(CacheType.IBK_TARGET_IO, intfId);
				} catch (Exception ex) {
					throw new ApiException("MCA TargetIo 캐시 파싱 오류[" + intfId + "]: " + ex.getMessage());
				}
				
				break;
			case "inMapping":
				try {
					tCacheInfo = cacheService.find(CacheType.IBK_MAPPING_IN, intfId);
				} catch (Exception ex) {
					throw new ApiException("MCA InMapping 캐시 파싱 오류[" + intfId + "]: " + ex.getMessage());
				}
				
				break;
			case "outMapping": 
				try {
					tCacheInfo = cacheService.find(CacheType.IBK_MAPPING_OUT, intfId);
				} catch (Exception ex) {
					throw new ApiException("MCA OutMapping 캐시 파싱 오류[" + intfId + "]: " + ex.getMessage());
				}
				
				break;
			default: 
				break;
		}
		
		return new ResultResponse(converter.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tCacheInfo));
	}
}
