package com.ibkglobal.integrator.engine.bean.mca.common;

import java.util.LinkedHashMap;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibk.ibkglobal.data.io.model.Tlgr;
import com.ibk.ibkglobal.data.mapp.TlgrMapping;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.config.TelegramConstants;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.converter.service.ConverterService;
import com.ibkglobal.message.struct.resource.ResourceMCA;

public class MappingMCA {

	@Autowired
	ResourceMCA resourceMCA;

	@Autowired
	ConverterService converterService;

	public void mappingExecute(Exchange exchange) throws IBKExceptionMCA {
		Message message = exchange.getIn();
		IBKMessage ibkMessage = null;

		try {
			ibkMessage = message.getBody(IBKMessage.class);
			
			// 개별부 데이터가 있을 때 개별부 매핑 실행
			if (ibkMessage.getStandardTelegram().getUserData() != null 
					&& ibkMessage.getStandardTelegram().getUserData().getData() != null && !ibkMessage.getInterfaceId().equals("ITRO00000035")) {
				
				LinkedHashMap<String, Object> mapping = mapping(message);
				if (mapping != null) {
					ibkMessage.getStandardTelegram().getUserData().setData(mapping);
				}
			}			
		} catch (Exception e) {
			throw new IBKExceptionMCA(ErrorType.MAPPING, "Mapping Error : " + ibkMessage.getInterfaceId(), e);
		}
	}

	public LinkedHashMap<String, Object> mapping(Message message) throws IBKExceptionMCA {
		IBKMessage ibkMessage = null;

		try {
			ibkMessage = message.getBody(IBKMessage.class);

			List<TlgrMapping> mappingList = null;
			List<Tlgr> targetList = null;

			// 매핑 여부 확인
			if ("Y".equals(resourceMCA.getInterface(ibkMessage.getInterfaceId()).getCommon().getAttribute().getMpngYn()
					.getCode())) {

				// 시스템 공통부 입력전문유형구분코드
				String inptTmgtDcd = ibkMessage.getStandardTelegram().getSttlSysCopt().getInptTmgtDcd();

				// 인바운드 / 아웃바운드
				String inOut = message.getHeader(ConstantCode.IN_OUT, String.class);
				
				if ("IN".equals(inOut)) {
					mappingList = resourceMCA.getMappingList(ibkMessage.getInterfaceId(), "IN");
					targetList = resourceMCA.getTargetIo(ibkMessage.getInterfaceId(), "OUT").getInBound().getFieldList();
					return converterService.mapping(mappingList, ibkMessage.getBody(), targetList, "IN", isNullable(inptTmgtDcd));
				} else if ("OUT".equals(inOut)) {
					mappingList = resourceMCA.getMappingList(ibkMessage.getInterfaceId(), "OUT");
					targetList = resourceMCA.getSourceIo(ibkMessage.getInterfaceId(), "IN").getOutBound().getFieldList();
					return converterService.mapping(mappingList, ibkMessage.getBody(), targetList, "OUT", isNullable(inptTmgtDcd));
				}
			}
		} catch (Exception e) {
			throw new IBKExceptionMCA(ErrorType.MAPPING, "Mapping Error : " + ibkMessage.getInterfaceId(), e);
		}

		return null;
	}

	/**
	 * Is null able
	 * 
	 * @param code
	 * @return
	 */
	private boolean isNullable(String code) throws IBKExceptionMCA {
		boolean nullable = true;
		TelegramConstants.InptTmgtDcd inptTmgtDcd = TelegramConstants.InptTmgtDcd.of(code);

		switch (inptTmgtDcd) {
		case MASS_OUT_CONTINUE_REQUEST:
		case MASS_OUT_STOP_REQUEST:
			nullable = false;
			break;
		default:
			nullable = true;
			break;
		}
		return nullable;
	}
}
