import { redirect } from "next/navigation";

export default function NotesIndexRedirectPage() {
  redirect("/library/public");
}
