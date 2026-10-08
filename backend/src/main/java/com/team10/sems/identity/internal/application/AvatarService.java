package com.team10.sems.identity.internal.application;

import com.team10.sems.identity.internal.persistence.UserAccountRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AvatarService {
    private final UserAccountRepository users;
    public AvatarService(UserAccountRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public AvatarView read(UUID user) {
        return new AvatarView(users.findById(user).orElseThrow(() -> new UserNotFoundException(user)).avatarData());
    }

    public AvatarView save(UUID user, String data) {
        String normalized = data == null ? null : normalize(data);
        var account = users.findById(user).orElseThrow(() -> new UserNotFoundException(user));
        account.updateAvatar(normalized);
        users.flush();
        return new AvatarView(normalized);
    }

    private String normalize(String data) {
        if (data.length() > 350000 || !data.matches("^data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+$")) throw invalid();
        try {
            byte[] bytes = Base64.getDecoder().decode(data.substring(data.indexOf(',') + 1));
            if (bytes.length > 262144) throw invalid();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName();
                    if (!(format.equalsIgnoreCase("png") || format.equalsIgnoreCase("jpeg"))
                            || reader.getWidth(0) > 512 || reader.getHeight(0) > 512) throw invalid();
                    var image = reader.read(0);
                    var output = new ByteArrayOutputStream();
                    ImageIO.write(image, "png", output); // Store decoded pixels only, without uploaded metadata.
                    return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch (java.io.IOException | IllegalArgumentException exception) { throw invalid(); }
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a PNG or JPEG avatar up to 512 × 512 pixels and 256 KB");
    }

    public record AvatarView(String dataUrl) {
        @Override public String toString() { return "AvatarView[image omitted]"; }
    }
}
