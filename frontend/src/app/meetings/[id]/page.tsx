import Link from "next/link";
import { Icon } from "@/components/icons";
import { MeetingPage } from "@/features/meetings/result";

export default async function Meeting({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <>
      <Link className="back-link" href="/meetings">
        <Icon name="back" />
        Все записи
      </Link>
      <MeetingPage id={id} />
    </>
  );
}
