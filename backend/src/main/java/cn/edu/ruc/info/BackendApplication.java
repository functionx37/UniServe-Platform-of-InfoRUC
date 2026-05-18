package cn.edu.ruc.info;

import org.mybatis.spring.annotation.MapperScan; // 必须新增这一行
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("cn.edu.ruc.info.mapper") // 必须新增这一行，指向你存放 Mapper 的包
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}