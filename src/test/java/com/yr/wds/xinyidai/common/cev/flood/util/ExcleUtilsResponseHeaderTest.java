package com.yr.wds.xinyidai.common.cev.flood.util;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcleUtilsResponseHeaderTest {

    @Test
    void shouldEncodeCommaSeparatedChineseFileNameAsSingleContentDisposition() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = ExcleUtils.class.getDeclaredMethod(
                "writeWorkbookResponse", XSSFWorkbook.class, javax.servlet.http.HttpServletResponse.class, String.class);
        method.setAccessible(true);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Data");
            method.invoke(new ExcleUtils(), workbook, response, "中塅水库,关山水库");
        }

        String contentDisposition = response.getHeader("Content-Disposition");
        assertEquals(1, response.getHeaders("Content-Disposition").size());
        assertTrue(contentDisposition.startsWith("attachment; filename=\""));
        assertTrue(contentDisposition.contains("\"; filename*=UTF-8''"));
        assertTrue(contentDisposition.contains("%2C"));
    }
}
