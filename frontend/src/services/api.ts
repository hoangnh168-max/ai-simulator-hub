
const API_BASE_URL = 'http://localhost:8080/api/v1';

export interface Simulator {
  id: string;
  title: string;
  description: string;
  subject: string;
  level: string;
  creator: string;
  url: string;
  status: 'Published' | 'Pending';
}

interface BackendSimulator {
  id: string;
  title: string;
  description: string | null;
  simulatorUrl: string;
  subject: string | null;
  educationLevel: string | null;
  status: string;
}

export interface CreateSimulatorRequest {
  title: string;
  description: string;
  simulatorUrl: string;
  subject: string;
  educationLevel: string;
}

function mapSimulator(data: BackendSimulator): Simulator {
  return {
    id: data.id,
    title: data.title,
    description: data.description ?? '',
    subject: data.subject ?? 'Other',
    level: data.educationLevel ?? 'All levels',
    creator: 'Community contributor',
    url: data.simulatorUrl,
    status: data.status === 'PUBLISHED' ? 'Published' : 'Pending',
  };
}

export async function getSimulators(): Promise<Simulator[]> {
  const response = await fetch(`${API_BASE_URL}/simulators`);

  if (!response.ok) {
    throw new Error(`Unable to retrieve simulators (${response.status})`);
  }

  const data: BackendSimulator[] = await response.json();
  return data.map(mapSimulator);
}

export async function createSimulator(
  data: CreateSimulatorRequest
): Promise<Simulator> {
  const response = await fetch(`${API_BASE_URL}/simulators`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(error || `Unable to register simulator (${response.status})`);
  }

  const result: BackendSimulator = await response.json();
  return mapSimulator(result);
}

export async function approveSimulator(id: string): Promise<Simulator> {
  const response = await fetch(`${API_BASE_URL}/simulators/${id}/approve`, {
    method: 'PATCH',
  });

  if (!response.ok) {
    throw new Error(`Unable to approve simulator (${response.status})`);
  }

  const result: BackendSimulator = await response.json();
  return mapSimulator(result);
}
