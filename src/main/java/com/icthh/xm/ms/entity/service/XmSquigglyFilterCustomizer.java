package com.icthh.xm.ms.entity.service;

import com.icthh.xm.commons.lep.LogicExtensionPoint;
import com.icthh.xm.commons.lep.spring.LepService;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import javax.servlet.http.HttpServletRequest;


@Slf4j
@LepService(group = "serialize.filter")
public class XmSquigglyFilterCustomizer {

    @LogicExtensionPoint("CustomizeFilter")
    public String customizeFilter(final String filter, final HttpServletRequest request, final Class beanClass) {
        log.debug("apply json filter for URL: {}, class: {}, current filter: {}",
                 getRequestUri(request), beanClass, filter);
        return filter;
    }

    private String getRequestUri(HttpServletRequest request) {
        return Optional.ofNullable(request)
                       .map(HttpServletRequest::getRequestURI)
                       .orElse(null);
    }

}
