import { z } from "zod";

// 서버 규칙(LoginRequest)과 같게 유지한다. 값은 트림하지 않고 서버로 그대로 보낸다.
export const loginSchema = z.object({
  username: z
    .string()
    .refine((value) => value.trim() !== "", "아이디를 입력하세요")
    .max(50, "아이디는 50자 이하로 입력하세요"),
  // 72바이트 초과는 서버가 인증 실패로 처리하므로 길이 상한을 두지 않는다.
  password: z.string().min(1, "비밀번호를 입력하세요"),
});

export type LoginValues = z.infer<typeof loginSchema>;
