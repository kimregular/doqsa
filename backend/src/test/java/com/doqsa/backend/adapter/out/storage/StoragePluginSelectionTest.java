package com.doqsa.backend.adapter.out.storage;

import com.doqsa.backend.adapter.out.storage.local.*;
import com.doqsa.backend.adapter.out.storage.s3.*;
import com.doqsa.backend.application.port.out.ImageStorage;
import com.doqsa.backend.global.OcrProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class StoragePluginSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OcrProperties.class)
            .withUserConfiguration(LocalStorageConfiguration.class, S3StorageConfiguration.class)
            .withPropertyValues("doqsa.storage.local-directory=./build/test-images/plugin",
                    "doqsa.storage.region=ap-northeast-2", "doqsa.storage.bucket=test-bucket");

    @Test void localPluginIsTheDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(ImageStorage.class)
                .getBean(ImageStorage.class).isInstanceOf(LocalImageStorage.class));
    }
    @Test void providerPropertySwapsInTheS3Plugin() {
        runner.withPropertyValues("doqsa.storage.provider=s3").run(context -> assertThat(context)
                .hasSingleBean(ImageStorage.class).doesNotHaveBean(LocalImageStorage.class)
                .getBean(ImageStorage.class).isInstanceOf(S3ImageStorage.class));
    }
    @Test void unknownProviderRegistersNoStorage() {
        runner.withPropertyValues("doqsa.storage.provider=supabase")
                .run(context -> assertThat(context).doesNotHaveBean(ImageStorage.class));
    }
}
